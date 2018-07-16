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
import org.hitachivantara.ci.build.helper.BuilderUtils
import org.hitachivantara.ci.build.impl.GradleBuilder
import org.hitachivantara.ci.config.BuildData
import org.hitachivantara.ci.config.BuildDataBuilder
import org.hitachivantara.ci.utils.ClosureInspectionHelper
import org.hitachivantara.ci.utils.MockDslScript
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class TestGradleBuild extends Specification {
  @Shared Script script = new MockDslScript()

  final static String FAKE_PATH = "workspace/fake/path"

  def "test that factory returns a gradle builder"() {
    setup:
      JobItem jobItem = new JobItem('', ['buildFramework': 'gradle'], [:])
      def builderData = [
          buildData: new BuildData(),
          dsl      : script
      ]

    when:
      IBuilder builder = BuilderFactory.getBuildManager(jobItem, builderData)

    then:
      builder instanceof GradleBuilder
  }

  @Unroll
  def "build command for #jobData"() {
    setup:
      JobItem jobItem = new JobItem('', jobData, [:])
      ClosureInspectionHelper helper = new ClosureInspectionHelper()
      BuildData buildData = new BuildDataBuilder(script)
          .withDefaults('test/resources/buildDefaults.yaml')
          .withParams([WORKSPACE: FAKE_PATH])
          .build()
      def builderData = [
          buildData: buildData,
          dsl      : script
      ]

    when:
      IBuilder builder = BuilderFactory.getBuildManager(jobItem, builderData)
      Closure gradleBuild = builder.getBuildClosure(jobItem)
      gradleBuild.resolveStrategy = Closure.DELEGATE_ONLY
      gradleBuild.delegate = helper
      gradleBuild()

    then:
      helper.cmds[0] == expected

    where:
      jobData                                                              || expected
      ['buildFramework': 'gradle']                                         || "gradle clean build --gradle-user-home=${FAKE_PATH}/caches/.gradle -x test"
      ['buildFramework': 'gradle', 'execType': 'noop']                     || null
      ['buildFramework': 'gradle', 'directives': '--info --console=plain'] || "gradle --info --console=plain --gradle-user-home=${FAKE_PATH}/caches/.gradle -x test"
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
      ['buildFramework': 'gradle']                                       || "gradle test "
      ['buildFramework': 'gradle', 'execType': 'noop']                   || null
      ['buildFramework': 'gradle', 'testable': false]                    || null
      ['buildFramework': 'gradle', 'settingsFile': 'my-settings.gradle'] || "gradle -c my-settings.gradle test "
  }

  @Unroll
  def "test directive [#directives]"() {
    setup:
      jobData.directives = directives
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
      Closure gradleTester = builder.getBuildClosure(jobItem)
      gradleTester.resolveStrategy = Closure.DELEGATE_ONLY
      gradleTester.delegate = helper
      gradleTester()

    then:
      helper.cmds[0] == expected

    where:
      jobData = ['buildFramework':'gradle']

      directives << [
          "${BuilderUtils.ADDITIVE_EXPR} -b testing.gradle",
          "${BuilderUtils.SUBTRACTIVE_EXPR} -q",
          "${BuilderUtils.ADDITIVE_EXPR} --build-cache ${BuilderUtils.SUBTRACTIVE_EXPR} clean"
      ]

      expected << [
        "gradle clean build -b testing.gradle --gradle-user-home=/caches/.gradle -x test",
        "gradle clean build --gradle-user-home=/caches/.gradle -x test",
        "gradle build --build-cache --gradle-user-home=/caches/.gradle -x test"
      ]
  }
}
