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
import org.apache.maven.project.MavenProject

/*
 * This is here for backwards compatibility.
 * maven-plugin comes with maven-core:3.1.0, which will be the default used to compile our shared libs
 * If the team responsible of maven-plugin upgrades it's maven minimal version, consider replacing this class.
 * DefaultProjectDependencyGraph was refactored in version 3.3.0
 * (org.apache.maven.DefaultProjectDependencyGraph -> org.apache.maven.graph.DefaultProjectDependencyGraph)
 * and no longer package protected.
 */
class DelegatedProjectDependencyGraph implements ProjectDependencyGraph {
  private ProjectDependencyGraph defaultProjectDependencyGraph

  DelegatedProjectDependencyGraph(List<MavenProject> projects) {
    this.defaultProjectDependencyGraph = new DefaultProjectDependencyGraph(projects)
  }

  @NonCPS
  List<MavenProject> getSortedProjects() {
    defaultProjectDependencyGraph.getSortedProjects()
  }

  @NonCPS
  List<MavenProject> getDownstreamProjects(MavenProject mavenProject, boolean transitive) {
    defaultProjectDependencyGraph.getDownstreamProjects(mavenProject, transitive)
  }

  @NonCPS
  List<MavenProject> getUpstreamProjects(MavenProject mavenProject, boolean transitive) {
    defaultProjectDependencyGraph.getUpstreamProjects(mavenProject, transitive)
  }
}
