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

import hudson.model.Result
import org.hitachivantara.ci.JobItem
import org.hitachivantara.ci.config.BuildData
import org.hitachivantara.ci.build.BuilderFactory
import org.hitachivantara.ci.build.IBuilder

import static org.hitachivantara.ci.config.BuildData.*

def call(BuildData buildData) {
  Map buildProperties = buildData.buildProperties
  Map buildMap = buildData.buildMap

  if (buildProperties.getBool(NOOP)) {
    log.info "NOOP: No tests will be ran."
    return
  }

  Boolean ignoreFailures = buildProperties.getBool(IGNORE_PIPELINE_FAILURE)

  log.info "Running unit tests..."
  log.info "Configured test chunk size: ${buildProperties[PARALLEL_UNIT_TESTS_CHUNKSIZE]}"

  // Collect all job items into a single list without noop or untestable items
  List<JobItem> jobItems = buildMap
      .collectMany { String key, List<JobItem> value -> value.findAll { JobItem ji -> !ji.execNoop && ji.testable } }
      .collect { JobItem jobItem ->
    IBuilder builder = BuilderFactory.getBuildManager(jobItem, [buildData: buildData, dsl: this])
    return jobItem.parallel ? builder.expandWorkItem(jobItem) : jobItem
  }.flatten()

  // if no chunk value was specified don't split it
  int chunkSize = buildProperties.getInt(PARALLEL_UNIT_TESTS_CHUNKSIZE) ?: jobItems.size()

  while (jobItems) {
    List jobItemsChunk = jobItems.size() > chunkSize ? jobItems[0..chunkSize - 1] : jobItems

    try {
      Map entries = jobItemsChunk.collectEntries { JobItem jobItem ->
        IBuilder builder = BuilderFactory.getBuildManager(jobItem, [buildData: buildData, dsl: this])
        Closure testExecution = builder.getTestClosure(jobItem)

        [(jobItem.jobID): {
          node(buildProperties[SLAVE_NODE_LABEL]) {
            utils.handleError(
                testExecution,
                { Throwable error ->
                  buildData.error(jobItem, error)
                  throw error
                },
                {
                  log.info "Archiving tests for job item ${jobItem.jobID} with pattern ${jobItem.testsArchivePattern}"
                  dir(jobItem.affectedPath ?: jobItem.buildWorkDir) {
                    junit allowEmptyResults: true, testResults: jobItem.testsArchivePattern
                  }
                })
          }
        }]
      }
      //failfast currently doesn't add much, because we are defining -Dmaven.test.failure.ignore=true on our settings.xml
      entries.failFast = !ignoreFailures
      parallel entries
    } catch (Exception e) {
      log.warn "Tests have failed: ${e}"

      if (ignoreFailures) {
        currentBuild.result = Result.UNSTABLE
      } else {
        error "$e"
      }
    }

    jobItems -= jobItemsChunk
  }

}