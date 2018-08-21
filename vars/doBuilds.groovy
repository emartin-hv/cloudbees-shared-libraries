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
import org.hitachivantara.ci.build.BuilderFactory
import org.hitachivantara.ci.build.IBuilder
import hudson.model.Result

import static org.hitachivantara.ci.build.helper.BuilderUtils.organizeItems
import static org.hitachivantara.ci.config.BuildData.IGNORE_PIPELINE_FAILURE
import static org.hitachivantara.ci.config.BuildData.NOOP
import static org.hitachivantara.ci.config.BuildData.SLAVE_NODE_LABEL

def call(BuildData buildData) {
  Map buildProperties = buildData.buildProperties
  Map buildMap = buildData.buildMap

  if (buildProperties.getBool(NOOP)) {
    log.info "NOOP: No builds performed."
    return
  }

  Boolean ignoreFailures = buildProperties.getBool(IGNORE_PIPELINE_FAILURE)

  log.info "Running builds..."

  buildMap.each { String jobGroup, List<JobItem> jobItems ->
    log.info "Running parallel job group [${jobGroup}]..."
    List buildableJobItems = jobItems.findAll { JobItem ji -> !ji.execNoop }

    // no jobItems to build, leave
    if (!buildableJobItems) {
      log.info "No job items to build for this group."
      return
    }

    try {
      List<List<?>> jobs = organizeItems(buildableJobItems.collect { JobItem jobItem ->
        IBuilder builder = BuilderFactory.getBuildManager(jobItem, [buildData: buildData, dsl: this])
        return jobItem.parallel ? builder.expandWorkItem(jobItem) : jobItem
      })

      jobs.each { List<JobItem> workItems ->
        Map entries = workItems.collectEntries { JobItem item ->
          IBuilder builder = BuilderFactory.getBuildManager(item, [buildData: buildData, dsl: this])

          // SUPER IMPORTANT! Grab our build configurations before entering a node
          Closure buildExecution = builder.getBuildClosure(item)

          [(item.jobID): {
            node(buildProperties[SLAVE_NODE_LABEL]) {
              utils.handleError(
                  buildExecution,
                  { Throwable err ->
                    buildData.error(item, err)
                    throw err
                  })
            }
          }]
        }
        entries.failFast = !ignoreFailures
        parallel entries
      }
    } catch (Throwable e) {
      if (ignoreFailures) {
        log.warn "Build is unstable: ${e}"
        currentBuild.result = Result.UNSTABLE
      } else {
        error "Build has failed: ${e}"
      }
    }
  }
}
