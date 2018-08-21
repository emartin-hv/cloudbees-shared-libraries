package org.hitachivantara.ci

import hudson.model.AbstractBuild
import hudson.model.Result
import hudson.model.Run
import hudson.scm.ChangeLogSet
import org.hitachivantara.ci.build.BuilderFactory
import org.hitachivantara.ci.build.impl.MavenBuilder
import org.hitachivantara.ci.config.BuildData
import org.hitachivantara.ci.config.BuildDataBuilder
import org.hitachivantara.ci.utils.ClosureInspectionHelper
import org.hitachivantara.ci.utils.MockDslScript
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class TestMavenChangeDetector extends Specification {
  @Shared
  Script script = new MockDslScript()
  @Shared
  Map builderData

  def setupSpec() {
    BuildData buildData = new BuildDataBuilder(script)
        .withDefaults('test/resources/buildDefaults.yaml')
        .build()
    builderData = [
        buildData: buildData,
        dsl      : script
    ]
  }

  def setup() {
    script.binding = new Binding()
  }

  def "no previous build yields commands"() {
    setup:
      JobItem jobItem = new JobItem('', ['buildFramework': 'Maven', 'execType': 'auto'], [:])
      MavenBuilder builder = BuilderFactory.getBuildManager(jobItem, builderData) as MavenBuilder
      ClosureInspectionHelper buildHelper = new ClosureInspectionHelper(delegate: script)
      ClosureInspectionHelper testHelper = new ClosureInspectionHelper(delegate: script)

    when: "there are no successful builds"
      def wrapper = GroovyMock(RunWrapper) {
        getRawBuild() >> Mock(Run)
      }
      script.binding.setVariable('currentBuild', wrapper)

    and:
      Closure mvnBuild = builder.getBuildClosure(jobItem)
      mvnBuild.resolveStrategy = Closure.DELEGATE_ONLY
      mvnBuild.delegate = buildHelper
      mvnBuild()

      Closure mvnTest = builder.getTestClosure(jobItem)
      mvnTest.resolveStrategy = Closure.DELEGATE_ONLY
      mvnTest.delegate = testHelper
      mvnTest()

    then: "the scripts yielded some commands"
      buildHelper.cmds
      testHelper.cmds
  }

  def "build is skipped if no changes detected"() {
    setup:
      JobItem jobItem = new JobItem('', ['buildFramework': 'Maven', 'execType': 'auto'], [:])
      MavenBuilder builder = BuilderFactory.getBuildManager(jobItem, builderData) as MavenBuilder
      ClosureInspectionHelper buildHelper = new ClosureInspectionHelper(delegate: script)
      ClosureInspectionHelper testHelper = new ClosureInspectionHelper(delegate: script)

    when: "there are no changes present"
      def prevBuild = Mock(AbstractBuild) {
        getNumber() >> 1
      }
      def wrapper = GroovyMock(RunWrapper) {
        getRawBuild() >> Mock(AbstractBuild) {
          getNumber() >> 2
          getPreviousBuild() >> prevBuild
          getPreviousSuccessfulBuild() >> prevBuild
        }
      }
      script.binding.setVariable('currentBuild', wrapper)

    and:
      Closure mvnBuild = builder.getBuildClosure(jobItem)
      mvnBuild.resolveStrategy = Closure.DELEGATE_ONLY
      mvnBuild.delegate = buildHelper
      mvnBuild()

      Closure mvnTest = builder.getTestClosure(jobItem)
      mvnTest.resolveStrategy = Closure.DELEGATE_ONLY
      mvnTest.delegate = testHelper
      mvnTest()

    then: "the scripts yielded no commands"
      buildHelper.cmds.empty
      testHelper.cmds.empty
  }

  @Unroll
  def "command build changes for #overrides"() {
    setup: "some mocking"
      def prevBuild = Mock(AbstractBuild) {
        getNumber() >> 1
      }
      def changeLogSet = GroovyMock(ChangeLogSet) {
        getItems() >> [Mock(ChangeLogSet.Entry) {
          getAffectedPaths() >> changes
        }]
      }

    and: "our job item and builder"
      JobItem jobItem = new JobItem('',
          ['buildFramework': 'Maven', 'execType': 'auto'] + overrides,
          ['BUILDS_ROOT_PATH': 'test/resources/multi-module-profiled-project'])
      MavenBuilder builder = BuilderFactory.getBuildManager(jobItem, builderData) as MavenBuilder
      ClosureInspectionHelper buildHelper = new ClosureInspectionHelper(delegate: script)

    when: "there are changes present"
      def wrapper = GroovyMock(RunWrapper) {
        getRawBuild() >> Mock(AbstractBuild) {
          getNumber() >> 2
          getPreviousBuild() >> prevBuild
          getPreviousSuccessfulBuild() >> prevBuild
          getChangeSets() >> [changeLogSet]
        }
      }
      script.binding.setVariable('currentBuild', wrapper)

    and: "we execute the build instance"
      Closure mvnBuild = builder.getBuildClosure(jobItem)
      mvnBuild.resolveStrategy = Closure.DELEGATE_ONLY
      mvnBuild.delegate = buildHelper
      mvnBuild()

    then: "the scripts yielded commands"
      buildHelper.cmds[0] == expected
      buildHelper.workdir[0] == workdir

    where:
      changes = ['sub-1/pom.xml', 'sub-3/pom.xml']

      overrides << [
          ['directives': "install"],
          ['directives': "install -DskipDefault -P profile-A"],
          ['directives': "install -pl sub-2,sub-3"],
          ['directives': "install", 'buildFile': "sub-1/pom.xml"],
          ['directives': "install", 'root': "sub-3"],
          ['directives': "install", 'root': "sub-2"],
      ]

      expected << [
          'mvn install -Daether.connector.resumeDownloads=false -DskipTests -f pom.xml -pl \'sub-1,sub-3\' -amd',
          'mvn install -Daether.connector.resumeDownloads=false -DskipTests -DskipDefault -P profile-A -f pom.xml -pl \'sub-1\' -amd',
          'mvn install -Daether.connector.resumeDownloads=false -DskipTests -f pom.xml -pl \'sub-3\' -amd',
          'mvn install -Daether.connector.resumeDownloads=false -DskipTests -f sub-1/pom.xml -amd',
          'mvn install -Daether.connector.resumeDownloads=false -DskipTests -f pom.xml -amd',
          null,
      ]

      workdir << [
          'test/resources/multi-module-profiled-project',
          'test/resources/multi-module-profiled-project',
          'test/resources/multi-module-profiled-project',
          'test/resources/multi-module-profiled-project',
          'test/resources/multi-module-profiled-project/sub-3',
          null,
      ]
  }

  @Unroll
  def "command test changes for #overrides"() {
    setup: "some mocking"
      def prevBuild = Mock(AbstractBuild) {
        getNumber() >> 1
      }
      def changeLogSet = GroovyMock(ChangeLogSet) {
        getItems() >> [Mock(ChangeLogSet.Entry) {
          getAffectedPaths() >> changes
        }]
      }

    and: "our job item and builder"
      JobItem jobItem = new JobItem('',
          ['buildFramework': 'Maven', 'execType': 'auto'] + overrides,
          ['BUILDS_ROOT_PATH': 'test/resources/multi-module-profiled-project'])
      MavenBuilder builder = BuilderFactory.getBuildManager(jobItem, builderData) as MavenBuilder
      ClosureInspectionHelper buildHelper = new ClosureInspectionHelper(delegate: script)

    when: "there are changes present"
      def wrapper = GroovyMock(RunWrapper) {
        getRawBuild() >> Mock(AbstractBuild) {
          getNumber() >> 2
          getPreviousBuild() >> prevBuild
          getPreviousSuccessfulBuild() >> prevBuild
          getChangeSets() >> [changeLogSet]
        }
      }
      script.binding.setVariable('currentBuild', wrapper)

    and: "we execute the build instance"
      Closure mvnBuild = builder.getTestClosure(jobItem)
      mvnBuild.resolveStrategy = Closure.DELEGATE_ONLY
      mvnBuild.delegate = buildHelper
      mvnBuild()

    then: "the scripts yielded commands"
      buildHelper.cmds[0] == expected
      buildHelper.workdir[0] == workdir

    where:
      changes = ['sub-1/pom.xml', 'sub-3/pom.xml', 'sub-3/sub-1/pom.xml']

      overrides << [
          ['directives': "install"],
          ['directives': "install -DskipDefault -P profile-A"],
          ['directives': "install -pl sub-2,sub-3"],
          ['directives': "install", 'buildFile': "sub-1/pom.xml"],
          ['directives': "install", 'root': "sub-3"],
          ['directives': "install", 'root': "sub-2"],
      ]

      expected << [
          'mvn test -Daether.connector.resumeDownloads=false -f pom.xml -pl \'sub-1,sub-3,sub-3/sub-1\' -amd',
          'mvn test -Daether.connector.resumeDownloads=false -DskipDefault -P profile-A -f pom.xml -pl \'sub-1\' -amd',
          'mvn test -Daether.connector.resumeDownloads=false -f pom.xml -pl \'sub-3\' -amd',
          'mvn test -Daether.connector.resumeDownloads=false -f sub-1/pom.xml -amd',
          'mvn test -Daether.connector.resumeDownloads=false -f pom.xml -amd',
          null,
      ]

      workdir << [
          'test/resources/multi-module-profiled-project',
          'test/resources/multi-module-profiled-project',
          'test/resources/multi-module-profiled-project',
          'test/resources/multi-module-profiled-project',
          'test/resources/multi-module-profiled-project/sub-3',
          null,
      ]
  }

  @Unroll
  def "test successive builds"() {
    setup: "some mocking"
      def changeLogSet = [
          [
              GroovyMock(ChangeLogSet) {
                getItems() >> [Mock(ChangeLogSet.Entry) {
                  getAffectedPaths() >> changes1.first()
                }]
              },
              changes1.last()
          ],
          [
              GroovyMock(ChangeLogSet) {
                getItems() >> [Mock(ChangeLogSet.Entry) {
                  getAffectedPaths() >> changes2.first()
                }]
              },
              changes2.last()
          ],
          [
              GroovyMock(ChangeLogSet) {
                getItems() >> [Mock(ChangeLogSet.Entry) {
                  getAffectedPaths() >> changes3.first()
                }]
              },
              changes3.last()
          ],
      ]
      def builds = []
      int lastSuccess = changeLogSet.findLastIndexOf { it.last() == Result.SUCCESS }
      changeLogSet.eachWithIndex { List comb, Integer i ->
        ChangeLogSet t = comb.first()
        Result result = comb.last()
        builds << Mock(AbstractBuild) {
          getNumber() >> (i + 1)
          getPreviousBuild() >> (i > 0 ? builds.last() : null)
          getPreviousSuccessfulBuild() >> ((i == 0 || lastSuccess < 0) ? null : builds[lastSuccess])
          getChangeSets() >> [t]
          getResult() >> result
        }
      }

      def wrapper = GroovyMock(RunWrapper) {
        getRawBuild() >> builds.last()
      }
      script.binding.setVariable('currentBuild', wrapper)

    and: "our job item and builder"
      JobItem jobItem = new JobItem('',
          ['buildFramework': 'Maven', 'execType': 'auto'],
          ['BUILDS_ROOT_PATH': 'test/resources/multi-module-profiled-project'])
      MavenBuilder builder = BuilderFactory.getBuildManager(jobItem, builderData) as MavenBuilder
      ClosureInspectionHelper buildHelper = new ClosureInspectionHelper(delegate: script)

    when: "we execute the build instance"
      Closure mvnBuild = builder.getBuildClosure(jobItem)
      mvnBuild.resolveStrategy = Closure.DELEGATE_ONLY
      mvnBuild.delegate = buildHelper
      mvnBuild()

    then: "the scripts yielded commands"
      buildHelper.cmds[0] == expected

    where:
      changes1                            | changes2                                             | changes3
      [[], Result.SUCCESS]                | [['sub-1/pom.xml'], Result.SUCCESS]                  | [['sub-3/pom.xml'], null]
      [['sub-1/pom.xml'], Result.SUCCESS] | [['sub-1/pom.xml', 'sub-3/pom.xml'], Result.FAILURE] | [[], null]
      [['sub-2/pom.xml'], Result.FAILURE] | [[], Result.FAILURE]                                 | [['sub-1/pom.xml', 'sub-3/pom.xml'], null]
      [['sub-2/pom.xml'], Result.SUCCESS] | [['sub-1/pom.xml'], Result.UNSTABLE]                 | [['sub-3/sub-1/pom.xml'], null]
      [[], Result.SUCCESS]                | [[], Result.SUCCESS]                                 | [['sub-2/pom.xml'], null]
      [[], Result.SUCCESS]                | [[], Result.SUCCESS]                                 | [['sub-2/sub-1/pom.xml'], null]
      [[], Result.SUCCESS]                | [[], Result.SUCCESS]                                 | [[], null]

      expected << [
          'mvn clean install -Daether.connector.resumeDownloads=false -DskipTests -f pom.xml -pl \'sub-3\' -amd',
          'mvn clean install -Daether.connector.resumeDownloads=false -DskipTests -f pom.xml -pl \'sub-1,sub-3\' -amd',
          'mvn clean install -Daether.connector.resumeDownloads=false -DskipTests',
          'mvn clean install -Daether.connector.resumeDownloads=false -DskipTests -f pom.xml -pl \'sub-1,sub-3/sub-1\' -amd',
          'mvn clean install -Daether.connector.resumeDownloads=false -DskipTests -f pom.xml -pl \'sub-2\' -amd',
          'mvn clean install -Daether.connector.resumeDownloads=false -DskipTests -f pom.xml -pl \'sub-2/sub-1\' -amd',
          null,
      ]
  }

}
