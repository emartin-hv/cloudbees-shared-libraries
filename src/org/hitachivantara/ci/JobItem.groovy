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

import com.cloudbees.groovy.cps.NonCPS
import org.hitachivantara.ci.config.FilteredMapWithDefault

import java.util.regex.Matcher
import java.util.regex.Pattern

class JobItem implements Serializable {

  // Build execution types
  static enum ExecutionType {
    AUTO,   // build only the changes
    FORCE,  // build no matter what
    NOOP    // don't build
  }

  // Build framework definitions
  static enum BuildFramework {
    AUTO, MAVEN, ANT, GRADLE
  }

  private static List<Pattern> GIT_URL_PATTERNS = [
      Pattern.compile(/https?:\/\/([\w-\.]+)\/(?<org>[\w-]+)\/(?<repo>[\w-]+)\.git/), // http/https
      Pattern.compile(/git@([\w-\.]+):(?<org>[\w-]+)\/(?<repo>[\w-]+)\.git/)          // ssh
  ]

  private Map<String, Object> data
  private Map<String, Object> buildProperties

  // Configurable properties
  private static final configurable = [
      [name: 'jobID', defaults: 'job' + UUID.randomUUID().toString()],
      [name: 'scmUrl', required: true],
      [name: 'scmBranch', defaults: 'master'],
      [name: 'directives', defaults: ''],
      [name: 'testable', defaults: true],
      [name: 'testsArchivePattern', defaults: '**/target/**/TEST*.xml'],
      [name: 'buildFile', defaults: null],
      [name: 'settingsFile', defaults: null],
      [name: 'root', defaults: ''],
      [name: 'versionProperty', defaults: null],
      [name: 'buildFramework', defaults: BuildFramework.MAVEN],
      [name: 'execType', defaults: ExecutionType.FORCE],
      [name: 'archivable', defaults: true],
      [name: 'artifactsArchivePattern', defaults: null]
  ]

  JobItem(String jobGroup, Map jobData, Map buildProperties) {
    this.buildProperties = buildProperties
    Map jobDefaults = buildProperties.JOB_ITEM_DEFAULTS ?: [:]

    // init
    data = new FilteredMapWithDefault(buildProperties)

    // set configuration
    configurable.each { Map config ->
      def value

      if (jobData.containsKey(config.name)) {
        // use given data
        value = jobData[config.name]
      } else if (jobDefaults.containsKey(config.name)) {
        // use default properties defaults
        value = jobDefaults[config.name]
      } else {
        // use hardcoded defaults
        value = config.defaults
      }

      set(config.name, value)
    }

    data.jobGroup = jobGroup
  }

  @NonCPS
  void setExecType(type) {
    switch (type) {
      case String:
        data.execType = ExecutionType.valueOf(type.toUpperCase())
        break
      case ExecutionType:
      case null:
        data.execType = type
        break
      default:
        throw new IOException("Invalid Execution Type $type")
    }
  }

  @NonCPS
  void setRoot(String root) {
    if (!root || root == '.') {
      data.root = ''
    } else {
      data.root = root
    }
  }

  @NonCPS
  void setBuildFramework(String framework) {
    switch (framework) {
      case String:
        data.buildFramework = BuildFramework.valueOf(framework.toUpperCase())
        break
      case ExecutionType:
      case null:
        data.buildFramework = framework
        break
      default:
        throw new IOException("Invalid Build Framework $framework")
    }
  }

  // this one needed?
  @NonCPS
  void setScmBranch(String scmBranch) {
    data.scmBranch = scmBranch
  }

  /**
   * Use the setter if it's available, set on the data directly if not
   * this allows us to do extra treatment on input in the setter if needed.
   *
   * NOTE: setters need to be marked with @NonCPS or initialization won't work
   *
   * @param property
   * @param value
   */
  @NonCPS
  void set(String property, value) {
    if (this.respondsTo("set${property.capitalize()}")) {
      this[property] = value
    } else {
      data[property] = value
    }
  }

  void setJobData(Map inData) {
    inData.each { String key, value ->
      set(key, value)
    }
  }

  String getJobGroup() {
    data.jobGroup
  }

  String getJobID() {
    data.jobID
  }

  String getScmUrl() {
    data.scmUrl
  }

  String getScmBranch() {
    data.scmBranch
  }

  BuildFramework getBuildFramework() {
    data.buildFramework as BuildFramework
  }

  String getDirectives() {
    data.directives
  }

  String getBuildFile() {
    data.buildFile
  }

  String getRoot() {
    data.root
  }

  ExecutionType getExecType() {
    data.execType as ExecutionType
  }

  String getVersionProperty() {
    data.versionProperty
  }

  String getSettingsFile() {
    data.settingsFile
  }

  Boolean isTestable() {
    data.testable
  }

  String getTestsArchivePattern(){
    data.testsArchivePattern
  }

  String getScmLabel() {
    if (!scmUrl) return '' // is empty a good value here?

    String repo, org

    GIT_URL_PATTERNS.each { Pattern pattern ->
      Matcher urlMatcher = (scmUrl =~ pattern)

      if (urlMatcher.matches()) {
        repo = urlMatcher.group('repo')
        org = urlMatcher.group('org')
      }
    }

    if (!repo || !org) {
      throw new IOException("Sorry, I don't know how to handle this kind of scm URL yet!\n  ${scmUrl}")
    }

    String scmLabel = "${org}.${repo}~${scmBranch}"

    if (buildProperties.ALLOW_ATOMIC_SCM_CHECKOUTS) {
      scmLabel += "~${jobGroup}.${jobID}"
    }

    return scmLabel
  }

  String getBuildWorkDir() {
    new File(checkoutDir + (root ? "/${root}" : '')).path
  }

  String getCheckoutDir() {
    new File(buildProperties.BUILDS_ROOT_PATH ? "${buildProperties.BUILDS_ROOT_PATH}/${scmLabel}" : scmLabel).path
  }

  boolean isExecForce() {
    data.execType == ExecutionType.FORCE
  }

  boolean isExecAuto() {
    data.execType == ExecutionType.AUTO
  }

  boolean isExecNoop() {
    data.execType == ExecutionType.NOOP
  }

  Boolean isArchivable(){
    return data.archivable
  }

  String getArtifactsArchivePattern(){
    data.artifactsArchivePattern
  }

  String toString() {
    // Iterating over the keyset and getting values here to force
    // property filtering. Iterating entries won't perform filtering.
    StringBuilder sb = new StringBuilder()
    Map jobData = getJobData()
    Set keySet = jobData.keySet().sort()
    keySet.remove('jobID')

    sb << "- jobID: ${jobData.jobID}"
    keySet.each { key ->
      sb << '\n'
      sb << "  ${key}: ${jobData[key]}"
    }

    return sb.toString()
  }

  Map getJobData() {
    // complement with non persistent data to provide an overview
    // of all the job data
    data + [
        scmLabel    : scmLabel,
        buildWorkDir: buildWorkDir,
        checkoutDir : checkoutDir
    ]
  }

}
