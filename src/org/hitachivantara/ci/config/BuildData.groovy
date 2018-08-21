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

package org.hitachivantara.ci.config

import org.hitachivantara.ci.JobItem


class BuildData implements Serializable {

  // TODO move these somewhere else?
  static final String ADD_BUILD_ID_VERSION_TAIL = 'ADD_BUILD_ID_VERSION_TAIL'
  static final String ALLOW_ATOMIC_SCM_CHECKOUTS = 'ALLOW_ATOMIC_SCM_CHECKOUTS'

  static final String ANT_DEFAULT_COMMAND_OPTIONS = 'ANT_DEFAULT_COMMAND_OPTIONS'
  static final String ANT_DEFAULT_DIRECTIVES = 'ANT_DEFAULT_DIRECTIVES'
  static final String JENKINS_ANT_FOR_BUILDS = 'JENKINS_ANT_FOR_BUILDS'
  static final String ANT_TEST_TARGETS = 'ANT_TEST_TARGETS'

  static final String ARCHIVE_ARTIFACTS = 'ARCHIVE_ARTIFACTS'
  static final String ARCHIVE_ARTIFACTS_PATTERN = 'ARCHIVE_ARTIFACTS_PATTERN'

  static final String BUILD_PLAN_ID = 'BUILD_PLAN_ID'
  static final String BUILDS_ROOT_PATH = 'BUILDS_ROOT_PATH'
  static final String BUILD_RETRIES = 'BUILD_RETRIES'
  static final String BUILD_TIMEOUT = 'BUILD_TIMEOUT'

  static final String CHECKOUT_CREDENTIALS_ID = 'CHECKOUT_CREDENTIALS_ID'
  static final String CHECKOUT_DEPTH = 'CHECKOUT_DEPTH'
  static final String CHECKOUT_SLEEP_AVERAGE_SECONDS = 'CHECKOUT_SLEEP_AVERAGE_SECONDS'
  static final String CHECKOUT_TIMEOUT_MINUTES = 'CHECKOUT_TIMEOUT_MINUTES'
  static final String CHECKOUT_TIMESTAMP = 'CHECKOUT_TIMESTAMP'

  static final String USE_DISTRIBUTED_SOURCE_CACHING = 'USE_DISTRIBUTED_SOURCE_CACHING'

  static final String GRADLE_DEFAULT_DIRECTIVES = 'GRADLE_DEFAULT_DIRECTIVES'
  static final String GRADLE_DEFAULT_COMMAND_OPTIONS = 'GRADLE_DEFAULT_COMMAND_OPTIONS'
  static final String GRADLE_TEST_TARGETS = 'GRADLE_TEST_TARGETS'
  static final String JENKINS_GRADLE_FOR_BUILDS = 'JENKINS_GRADLE_FOR_BUILDS'

  static final String JENKINS_JDK_FOR_BUILDS = 'JENKINS_JDK_FOR_BUILDS'
  static final String LIB_CACHE_ROOT_PATH = 'LIB_CACHE_ROOT_PATH'

  static final String MAVEN_DEFAULT_COMMAND_OPTIONS = 'MAVEN_DEFAULT_COMMAND_OPTIONS'
  static final String MAVEN_DEFAULT_DIRECTIVES = 'MAVEN_DEFAULT_DIRECTIVES'
  static final String MAVEN_RESOLVE_REPO_URL = 'MAVEN_RESOLVE_REPO_URL'
  static final String MAVEN_PUBLIC_RELEASE_REPO_URL = 'MAVEN_PUBLIC_RELEASE_REPO_URL'
  static final String MAVEN_PUBLIC_SNAPSHOT_REPO_URL = 'MAVEN_PUBLIC_SNAPSHOT_REPO_URL'
  static final String MAVEN_PRIVATE_RELEASE_REPO_URL = 'MAVEN_PRIVATE_RELEASE_REPO_URL'
  static final String MAVEN_PRIVATE_SNAPSHOT_REPO_URL = 'MAVEN_PRIVATE_SNAPSHOT_REPO_URL'
  static final String JENKINS_MAVEN_FOR_BUILDS = 'JENKINS_MAVEN_FOR_BUILDS'
  static final String MAVEN_OPTS = 'MAVEN_OPTS'
  static final String MAVEN_TEST_OPTS = 'MAVEN_TEST_OPTS'

  static final String NOOP = 'NOOP'
  static final String PARALLEL_CHECKOUT_CHUNKSIZE = 'PARALLEL_CHECKOUT_CHUNKSIZE'

  static final String RELEASE_MODE = 'RELEASE_MODE'
  static final String RELEASE_VERSION = 'RELEASE_VERSION'

  static final String SLAVE_NODE_LABEL = 'SLAVE_NODE_LABEL'
  static final String SUITE_RELEASE_VERSION = 'SUITE_RELEASE_VERSION'

  static final String TAG_NAME = 'TAG_NAME'
  static final String TAG_NAME_TYPE = 'TAG_NAME_TYPE'

  static final String PARALLEL_UNIT_TESTS_CHUNKSIZE = 'PARALLEL_UNIT_TESTS_CHUNKSIZE'
  static final String WORKSPACE = 'WORKSPACE'
  static final String STAGE_NAME = 'STAGE_NAME'


  static final String IBUILDER_RESOURCES_ROOT_PATH = 'IBUILDER_RESOURCES_ROOT_PATH'
  static final String INSTALL_BUILDER_ROOT_DIR = 'INSTALL_BUILDER_ROOT_DIR'

  static final String IGNORE_PIPELINE_FAILURE = 'IGNORE_PIPELINE_FAILURE'

  static final String ARCHIVE_TESTS_PATTERN = 'ARCHIVE_TESTS_PATTERN'

  static final String SLACK_INTEGRATION = 'SLACK_INTEGRATION'
  static final String SLACK_CHANNEL = 'SLACK_CHANNEL'
  static final String SLACK_TEAM_DOMAIN = 'SLACK_TEAM_DOMAIN'
  static final String SLACK_CREDENTIALS_ID = 'SLACK_CREDENTIALS_ID'

  /**
   * Holds JobItems in the groups defined in the configuration
   */
  Map<String, List<JobItem>> buildMap = [:]

  /**
   * Holds the global properties defined in the configuration
   */
  Map<String, Object> buildProperties = [:]

  /**
   * Holds the build's possibles errors by job id
   */
  Map<String, Map> buildStatus = [:]

  String getBuildPlanId() {
    buildProperties[BUILD_PLAN_ID]
  }

  String getReleaseVersion() {
    buildProperties[RELEASE_VERSION]
  }

  String getSuiteReleaseVersion() {
    buildProperties[SUITE_RELEASE_VERSION]
  }

  Boolean isReleaseMode() {
    Boolean.valueOf(buildProperties[RELEASE_MODE])
  }

  Boolean isAddBuildIDVersionTail() {
    buildProperties[ADD_BUILD_ID_VERSION_TAIL]
  }

  String getTagNameType() {
    buildProperties[TAG_NAME_TYPE]
  }

  String getTagName() {
    buildProperties[TAG_NAME]
  }

  String getSlaveNodeLabel() {
    buildProperties[SLAVE_NODE_LABEL]
  }

  Integer getParallelCheckoutChunksize() {
    buildProperties[PARALLEL_CHECKOUT_CHUNKSIZE] as Integer
  }

  Integer getBuildTimeout() {
    buildProperties[BUILD_TIMEOUT] as Integer
  }

  Boolean isNoop() {
    Boolean.valueOf( buildProperties[NOOP] )
  }

  // I would prefer to use concurrent maps, but... CPS didn't like it much
  synchronized void error(JobItem ji, Throwable e) {
    buildStatus
        .get('errors', [:])
        .get(buildProperties[STAGE_NAME], [:])
        .put(ji, e)
  }

  Boolean hasErrors(){
    buildStatus.errors as Boolean
  }

  String getErrorsString(int limit = 0) {
    int indent = 2
    StringBuilder sb = new StringBuilder()
    sb << 'Errors:'

    if (hasErrors()) {
      def spacer = { int current, int longest ->
        if (current == longest) return ' '
        ' ' * (longest - current + 1)
      }
      buildStatus.errors.each { String stage, Map<JobItem, ?> stageErrors ->
        Map errorsToList = (limit ? stageErrors.take(limit) : stageErrors)
        Set<JobItem> keys = errorsToList.keySet()
        int longest = keys.sort({a,b -> b.jobID.size() <=> a.jobID.size()})[0].jobID.size()

        sb << '\n' << ' ' * indent
        sb << "[${stage}]"
        keys.each { JobItem jobItem ->
          sb << '\n' << ' ' * indent*2
          sb << jobItem.jobID
          sb << spacer(jobItem.jobID.size(), longest) << ': '
          sb << jobItem.scmUrl
          sb << " (${jobItem.scmBranch})"
        }
        if (limit && stageErrors.size() > limit) {
          sb << '\n' << ' ' * indent*2
          sb << '(...)'
        }
      }
    } else {
      sb << '\n' << ' ' * indent
      sb << 'No errors'
    }

    return sb.toString()
  }

}
