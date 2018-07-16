package org.hitachivantara.ci

import org.hitachivantara.ci.config.BuildData
import org.hitachivantara.ci.config.BuildDataBuilder
import org.hitachivantara.ci.utils.MockDslScript
import spock.lang.Shared
import spock.lang.Specification

import static org.hitachivantara.ci.JobItem.ExecutionType.*
import static org.hitachivantara.ci.JobItem.BuildFramework.*

class TestConfiguration extends Specification {
  @Shared
  Script script = new MockDslScript()

  def "test configuration merging across sources"() {
    when:
      BuildData mbd = new BuildDataBuilder(script)
          .withEnvironment([
              DEFAULT_BUILD_PROPERTIES: 'test/resources/buildDefaults.yaml',
              AN_AMAZING_PROPERTY     : 'Environmental Value'
          ])
          .withParams([
              BUILD_DATA_FILE       : 'buildDataSample.yaml',
              AN_AMAZING_PROPERTY   : 'Paramental Value',
              ANT_DEFAULT_DIRECTIVES: 'clean-all'
          ])
          .build()

    then:
      noExceptionThrown()

    and:
      mbd.buildProperties == [
          BUILD_DATA_ROOT_PATH     : 'test/resources',
          BUILD_DATA_FILE          : 'buildDataSample.yaml',
          BUILDS_ROOT_PATH         : 'builds',
          ARCHIVE_ARTIFACTS_PATTERN: '**/dist/*.gz, **/dist/*.zip, **/target/*.gz, **/target/*.zip, **/build/**/*.gz, **/build/**/*.zip, **/build/*.zip',
          ANT_DEFAULT_DIRECTIVES   : 'clean-all',
          AN_AMAZING_PROPERTY      : 'Paramental Value',
          BUILD_PLAN_ID            : 'Amazing build plan',
          DEFAULT_BRANCH           : 9.0,
          GRADLE_DEFAULT_DIRECTIVES: 'clean build',
          GRADLE_TEST_TARGETS      : 'test',
          MAVEN_DEFAULT_DIRECTIVES : 'clean compile',
          PENTAHO_SCM_ROOT         : 'https://github.com/pentaho',
          SCM_HOST_ROOT            : 'https://github.com',
          WEBDETAILS_SCM_ROOT      : 'https://github.com/webdetails',
          ARCHIVE_TESTS_PATTERN    : '**/bin/**/TEST*.xml, **/target/**/TEST*.xml, **/build/**/*Test.xml',
          JOB_ITEM_DEFAULTS        : [
              scmUrl         : null,
              scmBranch      : '${DEFAULT_BRANCH}',
              buildFramework : 'MAVEN',
              buildFile      : 'pom.xml',
              directives     : '${MAVEN_DEFAULT_DIRECTIVES}',
              root           : '.',
              testable       : true,
              testsArchivePattern: '${ARCHIVE_TESTS_PATTERN}',
              versionProperty: null,
              execType       : 'AUTO',
              archivable    : true,
              artifactsArchivePattern : '${ARCHIVE_ARTIFACTS_PATTERN}'
          ]
      ]

    and: "check for the environment properties that need to be requested directly"
      mbd.buildProperties['DEFAULT_BUILD_PROPERTIES'] == 'test/resources/buildDefaults.yaml'
  }

  def "test job item defaults"() {
    when:
      BuildData mbd = new BuildDataBuilder(script)
          .withEnvironment([
              DEFAULT_BUILD_PROPERTIES: 'test/resources/buildDefaults.yaml',
          ])
          .withParams([
              BUILD_DATA_FILE: 'jobTestBuildData.yaml',
          ])
          .build()

    then:
      noExceptionThrown()

    and:
      JobItem jobItem1 = mbd.buildMap['20'][0]
      jobItem1.jobData == [
        buildFile          : 'pom.xml',
        settingsFile       : null,
        buildFramework     : MAVEN,
        buildWorkDir       : 'builds/pentaho.pentaho-ee~8.0/data-integration/plugins',
        checkoutDir        : 'builds/pentaho.pentaho-ee~8.0',
        directives         : 'clean install',
        execType           : AUTO,
        jobGroup           : '20',
        jobID              : 'pdi-plugins',
        root               : 'data-integration/plugins',
        scmBranch          : '8.0',
        scmLabel           : 'pentaho.pentaho-ee~8.0',
        scmUrl             : 'https://github.com/pentaho/pentaho-ee.git',
        testable           : false,
        testsArchivePattern: '**/bin/**/TEST*.xml, **/target/**/TEST*.xml, **/build/**/*Test.xml',
        versionProperty    : null,
        artifactsArchivePattern     : '**/dist/*.gz, **/dist/*.zip, **/target/*.gz, **/target/*.zip, **/build/**/*.gz, **/build/**/*.zip, **/build/*.zip',
        archivable         : true

      ]

  }


  def "test configuration filtering"() {
    when:
      BuildData mbd = new BuildDataBuilder(script)
          .withEnvironment([
          DEFAULT_BUILD_PROPERTIES: 'test/resources/buildDefaults.yaml',
          PROP_1                  : 'My ${PROP_2}',
          PROP_4                  : 'My ${PROP_5}'
      ])
          .withParams([
          BUILD_DATA_FILE: 'buildDataSample.yaml',
          PROP_2         : 'amazing ${PROP_3}',
          PROP_3         : 'property',
          PROP_5         : 'oi ${PROP_4}',
      ])
          .build()

    then:
      noExceptionThrown()

    and:
      mbd.buildProperties.subMap(['PROP_1', 'PROP_2', 'PROP_3']) == [
          PROP_1: 'My amazing property',
          PROP_2: 'amazing property',
          PROP_3: 'property'
      ]

  }

  def "test configuration filtering recursion"() {
    setup:
      BuildData mbd = new BuildDataBuilder(script)
          .withEnvironment([
          DEFAULT_BUILD_PROPERTIES: 'test/resources/buildDefaults.yaml',
          PROP_4                  : 'Go ${PROP_5}'
      ])
          .withParams([
          BUILD_DATA_FILE: 'buildDataSample.yaml',
          PROP_5         : 'back to ${PROP_4}',
      ])
          .build()

    when:
      mbd.buildProperties['PROP_4']

    then:
      thrown(InvalidPropertiesFormatException)

  }

  def "test job item defaults overriding at build file"() {
    when:
      BuildData bd = new BuildDataBuilder(script)
          .withDefaults('test/resources/jobDefaultsOverrideBuildDefaults.yaml')
          .withBuildData('test/resources/jobDefaultsOverrideBuildData.yaml')
          .build()

    then:
      noExceptionThrown()

    and:
      bd.buildProperties['JOB_ITEM_DEFAULTS'] == [
          'buildFile'     : 'pom.xml',
          'buildFramework': 'ANT',
          'directives'    : 'clean install',
          'scmBranch'     : 'master',
          'testable'      : true
      ]
  }

}
