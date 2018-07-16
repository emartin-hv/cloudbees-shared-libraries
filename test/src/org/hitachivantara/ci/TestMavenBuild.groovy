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

import hudson.model.Run
import org.hitachivantara.ci.build.BuilderFactory
import org.hitachivantara.ci.build.IBuilder
import org.hitachivantara.ci.build.helper.BuilderUtils
import org.hitachivantara.ci.build.impl.MavenBuilder
import org.hitachivantara.ci.config.BuildData
import org.hitachivantara.ci.config.BuildDataBuilder
import org.hitachivantara.ci.utils.ClosureInspectionHelper
import org.hitachivantara.ci.utils.MockDslScript
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class TestMavenBuild extends Specification {
  @Shared Script script

  def setupSpec() {
    script = new MockDslScript()
    script.binding = new Binding()
    def wrapper = GroovyMock(RunWrapper)
    wrapper.getRawBuild() >> GroovyMock(Run)
    script.binding.setVariable('currentBuild', wrapper)
  }

  def "test that factory returns a maven builder"() {
    setup:
      JobItem jobItem = new JobItem('', ['buildFramework': 'Maven'], [:])
      def builderData = [
          buildData: new BuildData(),
          dsl      : script
      ]

    when:
      IBuilder builder = BuilderFactory.getBuildManager(jobItem, builderData)

    then:
      builder instanceof MavenBuilder
  }

  @Unroll
  def "build command for #jobData"() {
    setup:
      JobItem jobItem = new JobItem('', jobData, [:])
      ClosureInspectionHelper helper = new ClosureInspectionHelper()
      BuildData buildData = new BuildDataBuilder(script)
          .withDefaults('test/resources/buildDefaults.yaml')
          .withParams([MAVEN_DEFAULT_COMMAND_OPTIONS: '-B -e'])
          .build()
      def builderData = [
          buildData: buildData,
          dsl      : script
      ]

    when:
      IBuilder builder = BuilderFactory.getBuildManager(jobItem, builderData)
      Closure mvnBuild = builder.getBuildClosure(jobItem)
      mvnBuild.resolveStrategy = Closure.DELEGATE_ONLY
      mvnBuild.delegate = helper
      mvnBuild()

    then:
      helper.cmds[0] == expected

    where:
      jobData << [
          ['buildFramework': 'Maven'],
          ['buildFramework': 'Maven', 'execType': 'noop'],
          ['buildFramework': 'Maven', 'directives': 'package', 'execType': 'force',],
          ['buildFramework': 'Maven', 'directives': 'install', 'buildFile': './core/pom.xml']
      ]

      expected << [
          'mvn clean install -B -e -DskipTests -Daether.connector.resumeDownloads=false',
          null,
          'mvn package -B -e -DskipTests -Daether.connector.resumeDownloads=false',
          'mvn install -B -e -f ./core/pom.xml -DskipTests -Daether.connector.resumeDownloads=false'
      ]
  }

  @Unroll
  def "test command for #jobData"() {
    setup:
      JobItem jobItem = new JobItem('', jobData, [:])
      ClosureInspectionHelper helper = new ClosureInspectionHelper()
      BuildData buildData = new BuildDataBuilder(script)
          .withDefaults('test/resources/buildDefaults.yaml')
          .withParams([MAVEN_TEST_OPTS:'-DsurefireArgLine=-Xmx1g'])
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
      jobData                                             || expected
      ['buildFramework': 'Maven']                         || 'mvn test -DsurefireArgLine=-Xmx1g -Daether.connector.resumeDownloads=false'
      ['buildFramework': 'Maven', 'execType': 'noop']     || null
      ['buildFramework': 'Maven', 'testable': false]      || null
      ['buildFramework': 'Maven', 'buildFile': 'pom.xml'] || 'mvn test -f pom.xml -DsurefireArgLine=-Xmx1g -Daether.connector.resumeDownloads=false'
  }

  @Unroll
  def "test directive [#directives]"() {
    setup:
      jobData.directives = directives
      JobItem jobItem = new JobItem('', jobData, [:])
      ClosureInspectionHelper helper = new ClosureInspectionHelper()
      BuildData buildData = new BuildDataBuilder(script)
          .withDefaults('test/resources/buildDefaults.yaml')
          .withParams([MAVEN_TEST_OPTS:'-DsurefireArgLine=-Xmx1g'])
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
      jobData = ['buildFramework': 'Maven']

      directives << [
          "${BuilderUtils.ADDITIVE_EXPR} -Daudit -P core",
          "${BuilderUtils.SUBTRACTIVE_EXPR} -B -e -DsurefireArgLine=-Xmx1g",
          "${BuilderUtils.ADDITIVE_EXPR} -Daudit ${BuilderUtils.SUBTRACTIVE_EXPR} -DsurefireArgLine=-Xmx1g",
          ""
      ]

      expected << [
          'mvn test -DsurefireArgLine=-Xmx1g -Daether.connector.resumeDownloads=false -Daudit -P core',
          'mvn test -Daether.connector.resumeDownloads=false',
          'mvn test -Daether.connector.resumeDownloads=false -Daudit',
          'mvn test -DsurefireArgLine=-Xmx1g -Daether.connector.resumeDownloads=false'
      ]
  }
}
