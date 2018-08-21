/*!
 * HITACHI VANTARA PROPRIETARY AND CONFIDENTIAL
 *
 * Copyright 2018 Hitachi Vantara. All rights reserved.
 *
 * NOTICE: All information including source code contained herein is, and
 * remains the sole property of Hitachi Vantara and its licensors. The intellectual
 * and technical concepts contained herein are proprietary and confidential
 * to, and are trade secrets of Hitachi Vantara and may be covered by U.S. and foreign
 * patents, or patents in process, and are protected by trade secret and
 * copyright laws. The receipt or possession of this source code and/or related
 * information does not convey or imply any rights to reproduce, disclose or
 * distribute its contents, or to manufacture, use, or sell anything that it
 * may describe, in whole or in part. Any reproduction, modification, distribution,
 * or public display of this information without the express written authorization
 * from Hitachi Vantara is strictly prohibited and in violation of applicable laws and
 * international treaties. Access to the source code contained herein is strictly
 * prohibited to anyone except those individuals and entities who have executed
 * confidentiality and non-disclosure agreements or other agreements with Hitachi Vantara,
 * explicitly covering such access.
 */

package org.hitachivantara.ci.build.tools.maven

import com.cloudbees.groovy.cps.NonCPS
import org.apache.maven.execution.ProjectDependencyGraph
import org.apache.maven.model.Model
import org.apache.maven.model.building.ModelBuildingResult
import org.apache.maven.project.MavenProject

import static org.hitachivantara.ci.build.tools.maven.MavenModule.allActiveModules
import static org.hitachivantara.ci.build.tools.maven.MavenModule.buildModelResult

class FilteredProjectDependencyGraph implements Serializable {
  private MavenModule module
  private List<String> activeProfiles
  private Properties userProperties
  private List<String> projectList

  FilteredProjectDependencyGraph(MavenModule module, List<String> activeProfiles,
                                 Properties properties, List<String> projectList) {
    this.module = module
    this.activeProfiles = activeProfiles
    this.userProperties = properties
    this.projectList = projectList
  }

  @NonCPS
  List<MavenProject> activeProjects() {
    ModelBuildingResult buildingResult = buildModelResult(module.pom, activeProfiles, userProperties)
    return getMavenProjects(buildingResult.effectiveModel.modules)
  }

  @NonCPS
  private List<MavenProject> getMavenProjects(Collection<String> modules) {
    modules.collect { String project ->
      Model model = buildModelResult(
          new File(module.pom.parentFile, "$project/pom.xml"),
          activeProfiles,
          userProperties).effectiveModel
      new MavenProject(model)
    }
  }

  @NonCPS
  ProjectDependencyGraph getDependencyGraph() {
    new DelegatedProjectDependencyGraph(activeProjects())
  }

  @NonCPS
  List<MavenProject> getSortedProjects() {
    ProjectDependencyGraph projectDependencyGraph = getDependencyGraph()
    List<MavenProject> sortedProjects = applyFilter(projectDependencyGraph.sortedProjects)
    return sortedProjects
  }

  List<List<String>> getSortedProjectsByGroups() {
    List<MavenProject> sortedProjects = getSortedProjects()
    List<List<MavenProject>> groupedProjects = sortedProjects
        .groupBy { it.projectReferences.keySet() }
        .inject([[]]) { List<List<MavenProject>> l, Set<String> k, List<MavenProject> v ->
      if (l.last().collect { getProjectReferenceId(it) }.disjoint(k)) {
        l.last().addAll(v)
      } else {
        l << v
      }
      return l
    }
    return groupedProjects.collect { List<MavenProject> m ->
      return m.collect { MavenProject p ->
        Model model = p.model
        String root = model.pomFile.parentFile.name
        List<String> result = [root]
        MavenModule rootModule = MavenModule.buildModule(model.pomFile)
        List<String> submodules = allActiveModules(rootModule, activeProfiles, userProperties)
        result += submodules.collect { "${root}/${it}".toString() }
        return result.join(',')
      }
    }
  }



  @NonCPS
  List<MavenProject> applyFilter(List<MavenProject> mavenProjects) {
    if (!projectList) return mavenProjects

    Map<MavenProject, ?> whiteList =
        getMavenProjects(projectList).collectEntries { [(it):null] }

    return mavenProjects.findAll { MavenProject project ->
      whiteList.containsKey(project)
    }
  }

  @NonCPS
  private static String getProjectReferenceId(MavenProject project) {
    return [project.groupId, project.artifactId, project.version].join(':')
  }

  @NonCPS
  String toString() {
    getSortedProjects().toString()
  }
}
