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

  if (buildProperties.getBool(NOOP)) {
    println "NOOP: No checkouts performed."
    return
  }

  println "Checking out projects..."
  println "Configured checkout chunk size: ${buildProperties.getInt(PARALLEL_CHECKOUT_CHUNKSIZE)}"

  // Collect all job items into a single list with distinct checkouts that are not NOOP
  List jobItems = buildMap.collectMany { String key, List value -> value }.findAll { !it.execNoop }
  jobItems.unique { it.scmLabel }

  // if no chunk value was specified don't split it
  int chunkSize = buildProperties.getInt(PARALLEL_CHECKOUT_CHUNKSIZE) ?: jobItems.size()

  String credentials = buildProperties[CHECKOUT_CREDENTIALS_ID]
  boolean useSourceCaching = buildProperties.getBool(USE_DISTRIBUTED_SOURCE_CACHING)
  boolean doPolling = !useSourceCaching
  int timeout = buildProperties.getInt(CHECKOUT_TIMEOUT_MINUTES)
  int depth = buildProperties.getInt(CHECKOUT_DEPTH)

  while (jobItems) {
    List jobItemsChunk = jobItems.size() > chunkSize ? jobItems[0..chunkSize - 1] : jobItems

    Map entries = jobItemsChunk.collectEntries { JobItem jobItem ->
      [(jobItem.scmLabel): {
        // To counter java.util.ConcurrentModificationException if needed.
        // https://issues.jenkins-ci.org/browse/JENKINS-34313
        int sleepAverage = buildProperties.getInt(CHECKOUT_SLEEP_AVERAGE_SECONDS)
        if (sleepAverage) {
          sleep(time: new Random().nextInt(sleepAverage * 1000), unit: 'MILLISECONDS')
        }

        node(buildProperties[SLAVE_NODE_LABEL]) {
          dir(jobItem.checkoutDir) {
            try {
              checkout(
                  poll: doPolling,
                  scm: [
                      $class                           : 'GitSCM',
                      branches                         : [[name: jobItem.scmBranch]],
                      doGenerateSubmoduleConfigurations: false,
                      extensions                       : [
                          [$class: 'CheckoutOption', timeout: timeout],
                          [$class: 'CloneOption', depth: depth, noTags: false, reference: '', shallow: true]
                      ],
                      submoduleCfg                     : [],
                      userRemoteConfigs                : [[credentialsId: credentials, url: jobItem.scmUrl]]
                  ]
              )

              // TODO: not currently used
              if (useSourceCaching) {
                stash(
                    name: jobItem.scmLabel,
                    allowEmpty: true,
                    useDefaultExcludes: false,
                    excludes: '**/dist, **/target, **/lib, **/lib-shared, **/lib-test, **/bin'
                )
              }
            } catch (Throwable e) {
              buildData.error(jobItem, e)
              throw e
            }
          }
        }
      }]
    }
    entries.failFast = true
    parallel entries

    jobItems -= jobItemsChunk
  }
}
