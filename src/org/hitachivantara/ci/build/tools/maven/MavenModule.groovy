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
import org.apache.maven.model.Model
import org.apache.maven.model.building.DefaultModelBuilderFactory
import org.apache.maven.model.building.DefaultModelBuildingRequest
import org.apache.maven.model.building.ModelBuilder
import org.apache.maven.model.building.ModelBuildingException
import org.apache.maven.model.building.ModelBuildingRequest
import org.apache.maven.model.building.ModelBuildingResult

import java.nio.file.Path

class MavenModule implements Serializable {
  String id
  String path
  File pom
  int depth
  MavenModule parent

  MavenModule(String id, File pom, MavenModule parent) {
    this.id = id
    this.pom = pom
    this.parent = parent
    this.depth = (parent == null ? 0 : parent.depth + 1)

    if (parent == null) {
      this.path = "."
    } else {
      File parentBaseDir = parent.pom.parentFile
      File moduleBaseDir = pom.parentFile
      this.path = parentBaseDir.toPath().normalize().relativize(moduleBaseDir.toPath().normalize())
    }
  }

  @NonCPS
  static MavenModule buildModule(ModelBuildingResult result) throws IOException {
    Model model = result.effectiveModel
    String id = "${model.groupId}:${model.artifactId}"

    // find the parents recursively cause that's all we need
    if (model.parent) {
      File parentPom = new File(model.projectDirectory, model.parent.relativePath)

      // we only care about local poms
      if (parentPom.exists()) {
        List<String> activeProfiles = result.modelIds.collectMany { result.getActivePomProfiles(it) }.collect { it.id }

        ModelBuildingResult parentResult = buildModelResult(parentPom, activeProfiles)
        Model parentModel = parentResult.effectiveModel
        String foundParentId = "${parentModel.groupId}:${parentModel.artifactId}"
        String referredParentId = "${model.parent.groupId}:${model.parent.artifactId}"

        Path parentPath = parentPom.parentFile.toPath().normalize()
        Path modulePath = model.pomFile.parentFile.toPath().normalize()
        String module = parentPath.relativize(modulePath).toString()

        // is the parent pom we found the actual parent we're referring to?
        // and
        // does the parent know this module exists?
        if (foundParentId == referredParentId && parentModel.modules.contains(module)) {
          return new MavenModule(id, model.pomFile, buildModule(parentResult))
        }
      }
    }

    new MavenModule(id, model.pomFile, null)
  }

  @NonCPS
  static MavenModule buildModule(File pom, List<String> activeProfiles = null, Properties userProperties = null) throws IOException {
    buildModule(buildModelResult(pom, activeProfiles, userProperties))
  }

  @NonCPS
  static ModelBuildingResult buildModelResult(File pom, List<String> activeProfiles = null, Properties userProperties = null) throws IOException {
    ModelBuilder builder = new DefaultModelBuilderFactory().newInstance()
    ModelBuildingResult result

    try {
      DefaultModelBuildingRequest request = new DefaultModelBuildingRequest()
          .setPomFile(pom)
          .setModelResolver(new OfflineModelResolver())
          .setTwoPhaseBuilding(true)
          .setSystemProperties(System.properties)
          .setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL)
          .setActiveProfileIds(activeProfiles)
          .setUserProperties(userProperties)

      result = builder.build(request)
    } catch (ModelBuildingException e) {
      throw new IOException(e)
    }

    return result
  }

  @NonCPS
  static List<String> activeModules(MavenModule module, List<String> activeProfiles = null, Properties properties = null) {
    ModelBuildingResult buildingResult = buildModelResult(module.pom, activeProfiles, properties)

    return buildingResult.effectiveModel.modules.collectMany { String effectiveModule ->
      MavenModule mavenModule = buildModule(new File(module.pom.parentFile,"$effectiveModule/pom.xml"))
      List<String> modules = activeModules(mavenModule, activeProfiles, properties)

      return [effectiveModule] + modules.collect { String inner -> "$effectiveModule/$inner".toString() }
    }
  }

  @NonCPS
  String getFullPath() {
    if (parent == null) {
      return "."
    }
    return (parent.fullPath == '.' ? '' : parent.fullPath + File.separator) + path
  }

  @NonCPS
  MavenModule getRoot() {
    parent == null ? this : parent.root
  }

  @NonCPS
  boolean equals(o) {
    if (this.is(o)) return true
    if (getClass() != o.class) return false

    MavenModule that = (MavenModule) o

    return id == that.id
  }

  @NonCPS
  int hashCode() {
    return (id != null ? id.hashCode() : 0)
  }

  @NonCPS
  String toString() {
    "MavenModule{" +
        "id=" + id +
        ", path=" + path +
        ", pom=" + pom +
        ", parent=" + (parent == null ? "<none>" : parent.id) +
        '}'
  }
}
