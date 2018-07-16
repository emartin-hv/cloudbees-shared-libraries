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
import org.hitachivantara.ci.build.impl.AntBuilder
import org.hitachivantara.ci.config.BuildData
import org.hitachivantara.ci.config.BuildDataBuilder
import org.hitachivantara.ci.utils.ClosureInspectionHelper
import org.hitachivantara.ci.utils.MockDslScript
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class TestAntBuild extends Specification {
  @Shared Script script = new MockDslScript()

  def "test that factory returns an ant builder"() {
    setup:
      JobItem jobItem = new JobItem('', ['buildFramework': 'Ant'], [:])
      def builderData = [
          buildData: new BuildData(),
          dsl      : script
      ]

    when:
      IBuilder builder = BuilderFactory.getBuildManager(jobItem, builderData)

    then:
      builder instanceof AntBuilder
  }

  @Unroll
  def "build command for #jobData"() {
    setup:
      JobItem jobItem = new JobItem('', jobData, [:])
      ClosureInspectionHelper helper = new ClosureInspectionHelper()
      BuildData buildData = new BuildDataBuilder(script)
          .withDefaults('test/resources/buildDefaults.yaml')
          .withParams([WORKSPACE: 'builds'])
          .build()
      def builderData = [
          buildData: buildData,
          dsl      : script
      ]

    when:
      IBuilder builder = BuilderFactory.getBuildManager(jobItem, builderData)
      Closure antBuild = builder.getBuildClosure(jobItem)
      antBuild.resolveStrategy = Closure.DELEGATE_ONLY
      antBuild.delegate = helper
      antBuild()

    then:
      helper.cmds[0] == expected

    where:
      jobData                                                       || expected
      ['buildFramework': 'Ant']                                     || 'ant -Divy.default.ivy.user.dir=builds/caches/.ivy2 clean-all resolve publish'
      ['buildFramework': 'Ant', 'execType':'noop']                  || null
      ['buildFramework': 'Ant', 'directives':'publish-local-nojar'] || 'ant -Divy.default.ivy.user.dir=builds/caches/.ivy2 publish-local-nojar'
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
      jobData                                      || expected
      ['buildFramework': 'Ant']                    || 'ant clean-all resolve publish'
      ['buildFramework': 'Ant', 'execType': 'noop']|| null
      ['buildFramework': 'Ant', 'testable': false] || null
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
      Closure mvnTest = builder.getTestClosure(jobItem)
      mvnTest.resolveStrategy = Closure.DELEGATE_ONLY
      mvnTest.delegate = helper
      mvnTest()

    then:
      helper.cmds[0] == expected

    where:
      jobData = ['buildFramework':'Ant']

      directives << [
          "${BuilderUtils.ADDITIVE_EXPR} -Dpentaho.resolve.repo=http://nexus.pentaho.org/content/groups/omni",
          "${BuilderUtils.SUBTRACTIVE_EXPR} publish-local",
          "${BuilderUtils.ADDITIVE_EXPR} -Dpentaho.resolve.repo=http://nexus.pentaho.org/content/groups/omni ${BuilderUtils.SUBTRACTIVE_EXPR} clean-all"
      ]

      expected << [
          'ant clean-all resolve publish -Dpentaho.resolve.repo=http://nexus.pentaho.org/content/groups/omni',
          'ant clean-all resolve publish',
          'ant resolve publish -Dpentaho.resolve.repo=http://nexus.pentaho.org/content/groups/omni'
      ]
  }
}
