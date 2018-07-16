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
import org.hitachivantara.ci.config.BuildDataBuilder
import org.hitachivantara.ci.config.BuildData
import org.hitachivantara.ci.CommitQuery
import org.hitachivantara.ci.GitHubApi

import static org.hitachivantara.ci.config.BuildData.*

def call() {

  BuildData buildData = new BuildDataBuilder(this)
    .withEnvironment(env)
    .withParams(params)
    .build()

  println "Configuration loaded for build plan: ${buildData.buildPlanId}"

  Map buildProperties = buildData.getBuildProperties()
  String scmCredsID = buildProperties[CHECKOUT_CREDENTIALS_ID]
  String checkoutTimestamp = buildProperties[CHECKOUT_TIMESTAMP]

  // TODO: The timestamp code below by default will wipeout any overrides in the BuildData object. To use
  // overrides, make sure to clear out the timestamp parameter in the build config prior to running the job. This
  // needs be fixed to allow precedence to jobItem overrides
  withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: scmCredsID, usernameVariable: 'GITUSER', passwordVariable: 'GITPASS']]) {
    // Using mapped build data here and retrieve most recent commits prior to running checkouts
    GitHubApi codeRepo = new GitHubApi(gitUser: GITUSER, gitPass: GITPASS)
    CommitQuery commitQuery = new CommitQuery(checkoutTimestamp: checkoutTimestamp, codeRepo: codeRepo)

    List allJobs = buildData.buildMap.collectMany { String jobGroup, List jobList -> jobList }
    Map scmLabeledJobs = allJobs.collectEntries { JobItem jobItem -> [(jobItem.scmLabel): jobItem] }

    commitQuery.retrieveTimestampShas(scmLabeledJobs)
  }

  return buildData
}
