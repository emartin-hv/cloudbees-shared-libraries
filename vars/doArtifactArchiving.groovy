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
import hudson.model.Result

import static org.hitachivantara.ci.config.BuildData.*

def call(BuildData buildData) {
  Map buildProperties = buildData.buildProperties
  Map buildMap = buildData.buildMap

  if (buildProperties.getBool(NOOP)) {
    println "NOOP: No archiving performed."
    return
  }

  Boolean ignoreFailures = buildProperties.getBool(IGNORE_PIPELINE_FAILURE)

  println "Running artifacts archiving..."

  buildMap.each { String jobGroup, List jobItems ->
    println "Running parallel job group [${jobGroup}]..."

    try {
      Map entries = jobItems.collectEntries { JobItem jobItem ->
        [(jobItem.jobID): {
          node(buildProperties[SLAVE_NODE_LABEL]) {
            try {
              if ( buildProperties[ARCHIVE_ARTIFACTS] && jobItem.isArchivable()) {
                println "Archiving artifacts for job item ${jobItem.jobID} with pattern ${jobItem.artifactsArchivePattern}"

                dir(jobItem.buildWorkDir) {
                  archiveArtifacts(
                    artifacts: jobItem.artifactsArchivePattern,
                    excludes: '.git/**/*',
                    allowEmptyArchive: true,
                    fingerprint: false
                  )
                }
              }
            } catch (Throwable e) {
              buildData.error(jobItem, e)
              throw e
            }
          }
        }]
      }
      entries.failFast = !ignoreFailures
      parallel entries
    } catch (Throwable e) {
      println "Artifact archiving has failed: ${e}"
      if (ignoreFailures) {
        currentBuild.result = Result.UNSTABLE
      } else {
        throw e
      }
    }
  }
}
