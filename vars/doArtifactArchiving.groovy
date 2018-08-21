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


import groovy.time.TimeCategory
import groovy.time.TimeDuration
import org.hitachivantara.ci.JobItem
import org.hitachivantara.ci.config.BuildData
import hudson.model.Result

import java.nio.file.Files
import java.nio.file.Paths

import static org.hitachivantara.ci.config.BuildData.*

def call(BuildData buildData) {
  Map buildProperties = buildData.buildProperties
  Map buildMap = buildData.buildMap

  if (buildProperties.getBool(NOOP)) {
    println "NOOP: No archiving performed."
    return
  }

  if (!buildProperties.getBool(ARCHIVE_ARTIFACTS)) {
    println "Artifact archiving is disabled (ARCHIVE_ARTIFACTS: false)."
    return
  }

  Date startDt = new Date()
  println "Running artifacts archiving."

  Boolean ignoreFailures = buildProperties.getBool(IGNORE_PIPELINE_FAILURE)
  Boolean copyToFolderAvailable = isCopyToFolderAvailable()

  if (copyToFolderAvailable) {
    println "Archiving artifacts by copying them to ${currentBuild.rawBuild.artifactsDir}"
  } else {
    println "Archiving artifacts resorting to the plugin"
  }

  try {
    buildMap.each { String jobGroup, List jobItems ->
      // filter out non archivable and noop
      List archivableJobItems = jobItems.findAll { JobItem ji -> !ji.execNoop && ji.archivable }

      // no jobItems to archive, leave
      if (!archivableJobItems) {
        println "No archivable job items for this group."
        return
      }

      Map entries = archivableJobItems.collectEntries { JobItem jobItem ->

        Closure archiveArtifacts = copyToFolderAvailable ?
            archiveArtifactsByCopy(jobItem, buildProperties.getBool(ALLOW_ATOMIC_SCM_CHECKOUTS)) : archiveArtifactsByPlugin(jobItem)

        [(jobItem.jobID): {
          node(buildProperties[SLAVE_NODE_LABEL]) {
            utils.handleError(
                archiveArtifacts,
                { Throwable e ->
                  buildData.error(jobItem, e)
                  throw e
                })
          }
        }]
      }
      entries.failFast = !ignoreFailures
      parallel entries
    }
  } catch (Throwable e) {
    println "Artifact archiving has failed: ${e}"
    if (ignoreFailures) {
      currentBuild.result = Result.UNSTABLE
    } else {
      throw e
    }
  } finally {
    Date endDt = new Date()
    TimeDuration td = TimeCategory.minus(endDt, startDt)
    println "Finished artifacts archiving [${td}]"
  }
}

Boolean isCopyToFolderAvailable() {
  return Files.exists(Paths.get(currentBuild.rawBuild.rootDir as String))
}

Closure archiveArtifactsByPlugin(JobItem jobItem) {
  return { ->
    dir(jobItem.buildWorkDir) {
      archiveArtifacts(
        artifacts: jobItem.artifactsArchivePattern,
        excludes: '.git/**/*, **/*-sources.jar',
        allowEmptyArchive: true,
        fingerprint: false
      )
    }
  }
}

Closure archiveArtifactsByCopy(JobItem jobItem, Boolean allowAtomicScmCheckouts) {

  String artifactsTargetDir = currentBuild.rawBuild.artifactsDir
  artifactsTargetDir = artifactsTargetDir + File.separator

  return { ->
    List<String> artifactPaths = getArtifactsPaths(jobItem.buildWorkDir, jobItem.artifactsArchivePattern, '.git/**/*, **/*-sources.jar')

    if (!artifactPaths.isEmpty()) {
      println """
Preparing artifacts archiving for job item ${jobItem.jobID}:
- pattern: ${jobItem.artifactsArchivePattern}
- files found: ${artifactPaths}
"""
      artifactPaths.each { String filePath ->
        String fileName = Paths.get(filePath).getFileName()
        String archiveFilePath = allowAtomicScmCheckouts ? (jobItem.getScmLabel() + File.separator + fileName) : fileName

        File targetFile = new File(artifactsTargetDir + archiveFilePath)
        if (!targetFile.exists()) {
          targetFile.getParentFile().mkdirs()
          Files.copy(Paths.get(filePath), targetFile.toPath())
        }
      }

    } else {
      println "No artifacts found for job item ${jobItem.jobID} with pattern: ${jobItem.artifactsArchivePattern}"
    }
  }
}

def getArtifactsPaths(final String rootFolder, final String includesPattern, final String excludesPattern) {
  List<String> artifactPaths = new FileNameFinder().getFileNames(rootFolder, includesPattern, excludesPattern)
  return artifactPaths?.unique()
}
