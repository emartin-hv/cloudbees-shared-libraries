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

import org.kohsuke.github.GitHub
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GHCommit

class GitHubApi {
  private GitHub github
  private String gitUser
  private String gitPass

  void establishConnection() {
    if (this.github) {
      return
    }

    if (!gitUser || !gitPass) {
      throw new Exception("GitHubApi credentials not found")
    }

    this.github = GitHub.connect(gitUser, gitPass)

    if (!this.github.isCredentialValid()) {
      throw new Exception("Could not connect to github")
    }
  }

  String retrieveSha(String orgRepo, String branch, long checkoutTime) {
    GHRepository repo

    try {
      repo = github.getRepository(orgRepo)
    } catch (Exception err) {
      throw new Exception("Unable to locate repository")
    }

    try {
      GHCommit commit = repo.queryCommits().from(branch).until(checkoutTime).pageSize(1).list()[0]

      if (commit == null) {
//        println "No commit history for repo prior to selected date"
        return null
      }

      return commit.getSHA1()
    } catch (Exception err) {
//      println "Unable to query ${orgRepo}:${branch} at ${checkoutTime}, defaulting to scmBranch"
//      println err
      return null
    }
  }
}

