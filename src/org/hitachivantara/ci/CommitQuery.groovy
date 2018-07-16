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

package org.hitachivantara.ci

import java.text.SimpleDateFormat

class CommitQuery {
  String checkoutTimestamp
  def codeRepo

  long parseTimeStamp() {
    String ts = this.checkoutTimestamp

    if (ts == null || ts == "" || ts == "CURRENT_TIME") {
      return 0
    } else if (!(ts ==~ /\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/)) {
      throw new Exception("Invalid format for the CHECKOUT_TIMESTAMP: ${ts}")
    }

    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    dateFormat.setLenient(false)
    try {
      return dateFormat.parse(ts).getTime()
    } catch (Exception err) {
      throw new Exception("Unable to parse the CHECKOUT_TIMESTAMP: ${ts}")
    }

  }

  void processJobs(jobScmMap, checkoutTime) {

    def keysArray = jobScmMap.keySet().toArray()

    for (int j = 0; j < jobScmMap.size(); j++) {
      def jobItem = jobScmMap.get(keysArray[j])
      // verify that org and repo are formatted correctly
      def orgRepoMatches = jobItem.getScmUrl() =~ /([\w\d\-_.]+\/[\w\d\-_.]+)\.git$/
      if (!orgRepoMatches) {
        throw new Exception("Invalid scmUrl format in buildData")
      }
      String orgRepo = orgRepoMatches[0][1]
//      println "${jobItem.getJobID()} : ${jobItem.getScmBranch()} : ${orgRepo}"
      def sha = codeRepo.retrieveSha(orgRepo, jobItem.getScmBranch(), checkoutTime)
      if (sha) {
        jobItem.setScmBranch(sha)
      }

    }
  }

  void retrieveTimestampShas(jobScmMap) {
    if (checkoutTimestamp == null || checkoutTimestamp == "") {
//      println "Blank timestamp, using default scm branches"
      return
    }

    long checkoutTime = parseTimeStamp() ?: new Date().getTime()

//    println "Will retrieve commits prior to ${new Date(checkoutTime)} (${checkoutTime})"

    codeRepo.establishConnection()

    processJobs(jobScmMap, checkoutTime)

  }
}
