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

/**
 * The JOB_NAME will be in one of the following formats:
 *
 * Simple:                          "jobname"
 * Simple inside a folder(s):       "folder1/folder2/jobname"
 * Multibranch:                     "jobname/branch"
 * Multibranch inside a folder(s):  "folder1/folder2/jobname/branch"
 *
 * The BRANCH_NAME variable will only be present if we are dealing with a
 * multibranch pipeline so we use that to identify that situation
 *
 * @return A filename based on the job name with a yaml extension
 */
def call() {
  String jobName = env.JOB_NAME
  String branchName = env.BRANCH_NAME

  // grab the last part if no branchName or the one before last if branchName exists
  List nameParts = jobName.tokenize('/')
  String buildFile = branchName ? nameParts[-2] : nameParts[-1]

  return "${buildFile}.yaml"
}
