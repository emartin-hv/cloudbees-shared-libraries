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

import org.hitachivantara.ci.build.BuilderFactory
import org.hitachivantara.ci.build.IBuilder
import org.hitachivantara.ci.build.impl.JenkinsJobBuilder
import org.hitachivantara.ci.config.BuildData
import org.hitachivantara.ci.config.BuildDataBuilder
import org.hitachivantara.ci.utils.ClosureInspectionHelper
import org.hitachivantara.ci.utils.MockDslScript
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class TestJenkinsJobBuild extends Specification {
  @Shared Script script = new MockDslScript()

  def "test that factory returns a jenkins job builder"() {
    setup:
      JobItem jobItem = new JobItem('', ['buildFramework': 'JENKINS_JOB'], [:])
      def builderData = [
          buildData: new BuildData(),
          dsl      : script
      ]

    when:
      IBuilder builder = BuilderFactory.getBuildManager(jobItem, builderData)

    then:
      builder instanceof JenkinsJobBuilder
  }

  @Unroll
  def "build command for #jobData"() {
    setup:
      JobItem jobItem = new JobItem('', jobData, [:])
      ClosureInspectionHelper helper = new ClosureInspectionHelper()
      BuildData buildData = new BuildDataBuilder(script)
          .withDefaults('test/resources/buildDefaults.yaml')
          .build()
      def builderData = [
          buildData: buildData,
          dsl      : script
      ]

    when:
      IBuilder builder = BuilderFactory.getBuildManager(jobItem, builderData)
      Closure build = builder.getBuildClosure(jobItem)
      build.resolveStrategy = Closure.DELEGATE_ONLY
      build.delegate = helper
      build()

    then:
      helper.cmds[0] == expected

    where:
      jobData                                                                                                                                      || expected
      ['buildFramework': 'JENKINS_JOB', 'targetJobName': 'target-job']                                                                             || "build( job: target-job, wait: true, parameters: [[name:MAVEN_DEFAULT_DIRECTIVES, value:clean install], [name:ANT_DEFAULT_DIRECTIVES, value:clean-all resolve publish], [name:GRADLE_DEFAULT_DIRECTIVES, value:clean build], [name:GRADLE_TEST_TARGETS, value:test], [name:SCM_HOST_ROOT, value:https://github.com], [name:PENTAHO_SCM_ROOT, value:https://github.com/pentaho], [name:WEBDETAILS_SCM_ROOT, value:https://github.com/webdetails], [name:DEFAULT_BRANCH, value:8.0], [name:AN_AMAZING_PROPERTY, value:Amazing Value], [name:BUILD_DATA_ROOT_PATH, value:test/resources], [name:BUILD_DATA_FILE, value:buildControlData.yaml], [name:BUILDS_ROOT_PATH, value:builds], [name:ARCHIVE_ARTIFACTS_PATTERN, value:**/dist/*.gz, **/dist/*.zip, **/target/*.gz, **/target/*.zip, **/build/**/*.gz, **/build/**/*.zip, **/build/*.zip], [name:ARCHIVE_TESTS_PATTERN, value:**/bin/**/TEST*.xml, **/target/**/TEST*.xml, **/build/**/*Test.xml]])"
      ['buildFramework': 'JENKINS_JOB', 'targetJobName': 'target-job', 'passOnBuildParameters': false, 'properties': [SOMETHING_ELSE: 'why not?']] || "build( job: target-job, wait: true, parameters: [[name:SOMETHING_ELSE, value:why not?]])"
      ['buildFramework': 'JENKINS_JOB', 'execType': 'noop']                                                                                        || null
  }

  @Unroll
  def "test command for #jobData"() {
    setup:
      JobItem jobItem = new JobItem('', jobData, [:])
      ClosureInspectionHelper helper = new ClosureInspectionHelper()
      BuildData buildData = new BuildDataBuilder(script)
          .withDefaults('test/resources/buildDefaults.yaml')
          .build()
      def builderData = [
          buildData: buildData,
          dsl      : script
      ]

    when:
      IBuilder builder = BuilderFactory.getBuildManager(jobItem, builderData)
      Closure mvnTest = builder.getTestClosure(jobItem)
      mvnTest.resolveStrategy = Closure.DELEGATE_ONLY
      mvnTest.delegate = helper
      mvnTest()

    then:
      helper.cmds[0] == expected

    where:
      jobData                                                            || expected
      ['buildFramework': 'JENKINS_JOB']                                  || null
  }
}
