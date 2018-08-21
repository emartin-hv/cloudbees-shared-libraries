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

import org.hitachivantara.ci.config.BuildData
import static org.hitachivantara.ci.config.BuildData.*

def call(BuildData buildData) {
  if (!buildData) {
    log.warn 'Empty Build Data. Cannot create reports.'
    return
  }

  // wrap all the reporting around a try cause we don't want failures here
  // a failure on post actions causes the real error not to show up in the log
  try {
    String messageString = generateMessage(buildData)

    // Do reports
    reportToSlack(buildData.buildProperties, messageString)
  } catch (Throwable e) {
    log.warn "Could not generate report: ${e.message}"
  }
}

String generateMessage(BuildData buildData){
  Map buildProperties = buildData.buildProperties
  StringBuilder message = new StringBuilder()

  message << "${buildProperties.JOB_NAME} #${currentBuild.number} (${currentBuild.durationString - ' and counting'})"
  message << '\n'

  try {
    // if RootUrl was not set on jenkins, currentBuild.absoluteUrl throws an exception
    message << currentBuild.absoluteUrl
    message << '\n'
  } catch (Throwable e) {}

  message << "Status: ${currentBuild.currentResult}"

  if (buildData.hasErrors()) {
    message << '\n'
    message << buildData.getErrorsString(10)

    // logging the errors, can help to quickly locate
    // the error origin when looking at the end of the log
    echo buildData.getErrorsString()
  }

  return message.toString()
}

void reportToSlack(Map buildProperties, String message) {
  if (buildProperties.getBool(SLACK_INTEGRATION)) {
    Map colorMapping = [
        SUCCESS : 'good',
        UNSTABLE: 'warning',
        FAILURE : 'danger',
        NOT_BUILT: '#838282',
        ABORTED: '#838282'
    ]
    String color = colorMapping[currentBuild.currentResult as String]

    slackSend(
        channel: buildProperties[SLACK_CHANNEL],
        teamDomain: buildProperties[SLACK_TEAM_DOMAIN],
        tokenCredentialId: buildProperties[SLACK_CREDENTIALS_ID],
        failOnError: false,

        color: color,
        message: message
    )
  }
}