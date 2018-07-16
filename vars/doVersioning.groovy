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

import org.hitachivantara.ci.JobItem
import org.hitachivantara.ci.config.BuildData
import static org.hitachivantara.ci.config.BuildData.*

def call(BuildData buildData) {
  Map buildProperties = buildData.buildProperties
  Map buildMap = buildData.buildMap

  String workspace = buildProperties[WORKSPACE]
  String resourcesConfigFolder = "${workspace}/resources/config"

  String versionsFileName = jobNameVersionsFileName()
  String versionsFile = "${resourcesConfigFolder}/${versionsFileName}"

  // Don't do anything if the versions file does not exist
  File propsFile = new File(versionsFile)
  if (!propsFile.exists()) {
    println "Skipping versioning. Versions file does not exist: ${versionsFileName}"
    return
  }

  // Get the version merger version from the version.properties file (default to 1.0.6)
  Properties properties = new Properties()
  properties.load(propsFile.newDataInputStream())
  def versionMergerVersion = properties.getProperty("version-merger.version", "1.0.6")

  // withMaven stuff
  String mavenSettingsFile = "${resourcesConfigFolder}/jenkins-pipeline-settings1.xml"
  String globalMavenSettingsFile = "${resourcesConfigFolder}/jenkins-pipeline-settings1.xml"
  String jdkID = buildProperties[JENKINS_JDK_FOR_BUILDS]
  String mavenID = buildProperties[JENKINS_MAVEN_FOR_BUILDS]
  def cacheRoot = buildProperties[LIB_CACHE_ROOT_PATH]
  String mavenLocalRepoPath = cacheRoot == null ? "${workspace}/caches/.m2/repository" : "${cacheRoot}/.m2/repository"
  String mavenDefaultExtraOptions = buildProperties[MAVEN_OPTS]

  // Grab version merger from the artifact repository, put it here
  def versionMergerJarPath = "${workspace}/version-merger.jar"

  dir(buildProperties[BUILDS_ROOT_PATH]) {
    withEnv(["RESOLVE_REPO_MIRROR=${buildProperties.MAVEN_RESOLVE_REPO_URL}"]) {
      withMaven(
        mavenSettingsFilePath: mavenSettingsFile,
        globalMavenSettingsFilePath: globalMavenSettingsFile,
        jdk: jdkID,
        maven: mavenID,
        mavenLocalRepo: mavenLocalRepoPath,
        mavenOpts: mavenDefaultExtraOptions,
        publisherStrategy: 'EXPLICIT') {
        sh "mvn -q dependency:get -Dtransitive=false -Dartifact=pentaho:version-merger:${versionMergerVersion}:jar -Ddest=${versionMergerJarPath}"
      }
    }
  }

  boolean releaseMode = buildData.releaseMode
  String releaseBuildNumber = buildProperties.RELEASE_BUILD_NUMBER ?: env.BUILD_NUMBER
  String buildIDTail = "-${releaseBuildNumber}"

  String releaseVersion = buildData.releaseVersion

  if (buildData.addBuildIDVersionTail) {
    releaseVersion = "${buildData.releaseVersion}-${releaseBuildNumber}"
  }

  // Update version.properties in the resources config folder
  dir(resourcesConfigFolder) {
    def matchPattern = '"(.*_REVISION$)|(^dependency\\..+\\.revision$)|(.*\\.version$)"'
    sh "java -DMATCH_PATTERN=${matchPattern} -DTARGET_FILES=\"${versionsFileName}\" -DRELEASE_MODE=${releaseMode} -DADD_BUILD_ID_TAIL_VERSION=${buildIDTail} -jar ${versionMergerJarPath} . -v -f ${versionsFile} commit -version ${buildData.suiteReleaseVersion}"
  }

  // Now that the version.properties have been updated, reload them
  properties.clear()
  properties.load(propsFile.newDataInputStream())

  // For each repo, run version merger
  buildMap.each { groupItem ->
    def jobItems = groupItem.getValue()
    jobItems.each { jobItem ->

      JobItem ji = (JobItem) jobItem
      if (buildData.isNoop() || ji.isExecNoop()) {
        println "NoOp: Update versions: ${ji.jobID}"
        return
      } else {

        def scmBranch = ji.scmBranch
        def jobVersion = properties.getProperty(ji.versionProperty, releaseVersion)

        dir(ji.getBuildWorkDir()) {
          println "Updating versions for ${ji.jobID}"
          sh "java -DRELEASE_MODE=${releaseMode} -jar ${versionMergerJarPath} . -f ${versionsFile} commit project.revision=${jobVersion} project.version=${jobVersion} version=${jobVersion} distribution.version=${jobVersion} project.stage=${scmBranch} || true"
        }
      }
    }
  }

}

def jobNameVersionsFileName() {
  String jobName = env.JOB_NAME
  String branchName = env.BRANCH_NAME

  // grab the last part if no branchName or the one before last if branchName exists
  List nameParts = jobName.tokenize('/')
  String buildFile = branchName ? nameParts[-2] : nameParts[-1]

  return "${buildFile}.versions"
}
