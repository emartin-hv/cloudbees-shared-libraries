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

import org.hitachivantara.ci.config.BuildData
import org.hitachivantara.ci.config.BuildDataBuilder
import org.hitachivantara.ci.utils.MockDslScript
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static org.hitachivantara.ci.JobItem.ExecutionType.*
import static org.hitachivantara.ci.JobItem.BuildFramework.*

class TestMappedBuildData extends Specification {
  @Shared
  Script script = new MockDslScript()

  def "test getting mapped data"() {
    when:
      BuildData mbd = new BuildDataBuilder(script)
          .withBuildData('test/resources/thinBuildDataTestFile.yaml')
          .withEnvironment([BUILDS_ROOT_PATH: 'builds'])
          .build()

      List allJobs = mbd.buildMap.collectMany { String jobGroup, List jobList -> jobList }

    then:
      mbd.buildPlanId == 'Jibberish Build Test'

    and:
      mbd.buildProperties == [
          ANT_DEFAULT_DIRECTIVES   : 'clean-all resolve publish',
          BUILD_PLAN_ID            : 'Jibberish Build Test',
          DEFAULT_BRANCH           : 8.0,
          GRADLE_DEFAULT_DIRECTIVES: '-q',
          MAVEN_DEFAULT_DIRECTIVES : 'clean install',
          PENTAHO_SCM_ROOT         : 'https://github.com/pentaho',
          SCM_HOST_ROOT            : 'https://github.com',
          WEBDETAILS_SCM_ROOT      : 'https://github.com/webdetails'
      ]

    and:
      mbd.buildMap.keySet() == ['1', '2', '3'] as Set
      (mbd.buildMap['1'] as List).size() == 1
      (mbd.buildMap['2'] as List).size() == 2
      (mbd.buildMap['3'] as List).size() == 4

    and:
      // not checking all jobs here as it would become too extensive, assuming a couple is enough
      JobItem jobItem1 = (mbd.buildMap['2'] as List<JobItem>)[0]
      jobItem1.jobData == [
          jobID                  : 'database-model',
          jobGroup               : '2',
          scmUrl                 : 'https://github.com/pentaho/pentaho-commons-database.git',
          scmBranch              : '8.0',
          scmLabel               : 'pentaho.pentaho-commons-database~8.0',
          buildFramework         : MAVEN,
          directives             : '+= -pl .,model',
          settingsFile           : null,
          testable               : true,
          testsArchivePattern    : '**/target/**/TEST*.xml',
          buildFile              : null,
          root                   : '',
          buildWorkDir           : 'builds/pentaho.pentaho-commons-database~8.0',
          checkoutDir            : 'builds/pentaho.pentaho-commons-database~8.0',
          versionProperty        : null,
          execType               : AUTO,
          archivable             : true,
          artifactsArchivePattern: null,
          parallelize            : false,
          asynchronous           : false,
          passOnBuildParameters  : true,
          properties             : null,
          targetJobName          : null
      ]

      JobItem jobItem2 = (mbd.buildMap['2'] as List<JobItem>)[1]
      jobItem2.jobData == [
          jobID                  : 'versionchecker',
          jobGroup               : '2',
          scmUrl                 : 'https://github.com/pentaho/pentaho-versionchecker.git',
          scmBranch              : '8.0',
          scmLabel               : 'pentaho.pentaho-versionchecker~8.0',
          buildFramework         : MAVEN,
          directives             : null,
          settingsFile           : null,
          testable               : true,
          testsArchivePattern    : '**/target/**/TEST*.xml',
          buildFile              : null,
          root                   : '',
          buildWorkDir           : 'builds/pentaho.pentaho-versionchecker~8.0',
          checkoutDir            : 'builds/pentaho.pentaho-versionchecker~8.0',
          versionProperty        : null,
          execType               : AUTO,
          archivable             : true,
          artifactsArchivePattern: null,
          parallelize            : false,
          asynchronous           : false,
          passOnBuildParameters  : true,
          properties             : null,
          targetJobName          : null
      ]

    and:
      allJobs.collectEntries { JobItem ji -> [(ji.scmLabel): ji.jobID] } == [
          'pentaho.pentaho-commons-database~8.0': 'database-model',
          'pentaho.pentaho-data-refinery~8.0'   : 'data-refinery',
          'pentaho.pentaho-ee~8.0'              : 'pdi-plugins',
          'pentaho.pentaho-versionchecker~8.0'  : 'versionchecker',
          'pentaho.maven-parent-poms~8.0'       : 'parent-poms',
          'webdetails.cgg~8.0'                  : 'cgg-plugin',
          'webdetails.sparkl~8.0'               : 'sparkl-plugin'
      ]
  }

  @Unroll
  def "test first and last job #firstJob:#lastJob"() {
    when:
      BuildData mbd = new BuildDataBuilder(script)
          .withBuildData('test/resources/thinBuildDataTestFile.yaml')
          .withParams([FIRST_JOB: firstJob, LAST_JOB: lastJob])
          .build()

    then:
      List jobItems = []
      mbd.buildMap.each { k, v ->
        jobItems += v.collect { JobItem ji ->
          [jobID: ji.jobID, execType: ji.execType]
        }
      }

      // get a list of all jobIds grouped by Execution Type
      Map result = jobItems.groupBy { it.execType }.collectEntries { k, v -> [(k): v.collect { it.jobID }] }

      result == expected

    where:
      firstJob << [
          'sparkl-plugin',
          '',
          'database-model'
      ]

      lastJob << [
          null,
          'sparkl-plugin',
          'sparkl-plugin'
      ]

      expected << [
          [(NOOP): ['parent-poms', 'database-model', 'versionchecker'], (AUTO): ['sparkl-plugin', 'cgg-plugin', 'data-refinery', 'pdi-plugins']],
          [(FORCE): ['parent-poms'], (AUTO): ['database-model', 'versionchecker', 'sparkl-plugin'], (NOOP): ['cgg-plugin', 'data-refinery', 'pdi-plugins']],
          [(NOOP): ['parent-poms', 'cgg-plugin', 'data-refinery', 'pdi-plugins'], (AUTO): ['database-model', 'versionchecker', 'sparkl-plugin']]
      ]
  }


  @Unroll
  def "test override job fields"() {
    when:
      BuildData mbd = new BuildDataBuilder(script)
          .withBuildData('test/resources/overrideJobParamBuildData.yaml')
          .withParams([OVERRIDE_JOB_PARAMS: override])
          .withEnvironment([
            PENTAHO_SCM_ROOT: 'https://github.com/pentaho/',
            DEFAULT_BRANCH: '8.0'
          ])
          .build()

    then:
      noExceptionThrown()

    and:
      if (expected) {
        expected.each { jobGroup, jobs ->
          jobs.each { jobID, jobParams ->
            Map resultJobData = mbd.buildMap[jobGroup].find { JobItem jobItem -> jobItem.jobID == jobID }.jobData
            jobParams.each { param, expectedValue ->
              def resultValue = resultJobData[param]

              assert resultValue == expectedValue
            }
          }
        }
      }

    where:
      override                                                || expected
      '- {jobID: parent-poms, directives: -DskipTests}'       || [ '1': ['parent-poms': ['directives': '-DskipTests']]]
      '''
      - jobID: parent-poms
        execType: AUTO
        directives: -DskipTests
      - jobID: versionchecker
        root: core
      '''                                                     || [ '1': ['parent-poms': ['execType': AUTO, 'directives': '-DskipTests']],
                                                                   '2': ['versionchecker': ['root': 'core']]]
      ' '                                                     || [:]
      '- {jobID: parent-poms, directives:}'                   || [ '1': ['parent-poms': ['directives': null]]]
      '''
        jobID: parent-poms
        directives: -DskipTests=false
      '''                                                     || [ '1': ['parent-poms': ['directives': '-DskipTests=false']]]
      '''
        jobID: parent-poms
        scmBranch: dev
      '''                                                     || [ '1': ['parent-poms': ['scmLabel': 'pentaho.maven-parent-poms~dev']]]
  }

  @Unroll
  def "test override job fields errors"() {
    when:
      new BuildDataBuilder(script)
          .withBuildData('test/resources/overrideJobParamBuildData.yaml')
          .withEnvironment([
            BUILDS_ROOT_PATH: 'builds'
          ])
          .withParams([OVERRIDE_JOB_PARAMS: override])
          .build()

    then:
      thrown(error)

    where:
      override                                          || error
      '''
        jobID: parent-poms
          directives: -DskipTests
      '''                                               || IllegalArgumentException
      '- {jobID: parent-poms directives: -DskipTests}'  || IllegalArgumentException
  }

  def "test getting mapped data with allow atomic scm checkouts"() {
    when:
      BuildData mbd = new BuildDataBuilder(script)
          .withBuildData('test/resources/allowAtomicBuildDataTestFile.yaml')
          .withEnvironment([
            BUILDS_ROOT_PATH: 'builds'
          ])
          .build()

      List allJobs = mbd.buildMap.collectMany { String jobGroup, List jobList -> jobList }

    then:
      mbd.buildProperties == [
          ANT_DEFAULT_DIRECTIVES    : 'clean-all resolve publish',
          BUILD_PLAN_ID             : 'Atomic SCM checkouts Build Test',
          DEFAULT_BRANCH            : 8.0,
          GRADLE_DEFAULT_DIRECTIVES : '-q',
          MAVEN_DEFAULT_DIRECTIVES  : 'clean install',
          ALLOW_ATOMIC_SCM_CHECKOUTS: true,
          PENTAHO_SCM_ROOT          : 'https://github.com/pentaho/',
          SCM_HOST_ROOT             : 'https://github.com/',
          WEBDETAILS_SCM_ROOT       : 'https://github.com/webdetails/'
      ]

    and:
      allJobs.collectEntries { JobItem ji -> [(ji.scmLabel): ji.jobID] } == [
          'pentaho.maven-parent-poms~8.0~1.parent-poms'          : 'parent-poms',
          'pentaho.pentaho-commons-database~8.0~2.database-model': 'database-model',
          'pentaho.pentaho-r-plugin~8.0~3.pdi-r-plugin-release'  : 'pdi-r-plugin-release',
          'pentaho.pentaho-r-plugin~8.0~3.pdi-r-plugin'          : 'pdi-r-plugin',
          'pentaho.pentaho-versionchecker~8.0~2.versionchecker'  : 'versionchecker'
      ]

    and:
      allJobs.collectEntries { JobItem ji -> [(ji.buildWorkDir): ji.jobID] } == [
          'builds/pentaho.maven-parent-poms~8.0~1.parent-poms'          : 'parent-poms',
          'builds/pentaho.pentaho-commons-database~8.0~2.database-model': 'database-model',
          'builds/pentaho.pentaho-r-plugin~8.0~3.pdi-r-plugin-release'  : 'pdi-r-plugin-release',
          'builds/pentaho.pentaho-r-plugin~8.0~3.pdi-r-plugin'          : 'pdi-r-plugin',
          'builds/pentaho.pentaho-versionchecker~8.0~2.versionchecker'  : 'versionchecker'
      ]

  }

  def "test override params with the OVERRIDE_PARAMS param"() {
    when:
      BuildData bd = new BuildDataBuilder(script)
          .withEnvironment(
            BUILD_DATA_ROOT_PATH: 'test/resources'
          )
          .withParams(
            OVERRIDE_PARAMS:
              '''
              PARAM_TO_OVERRIDE: overriden
              BUILD_DATA_FILE: 'overrideParamsBuildData.yaml'
              JOB_ITEM_DEFAULTS :
                scmBranch: dev  
              ''',
            BUILD_DATA_FILE: 'fakeBuildData.yaml'
          )
          .build()

    then:
      noExceptionThrown()

    and:
      bd.buildProperties == [
          BUILD_PLAN_ID: 'Test param overrides',
          BUILD_DATA_FILE: 'overrideParamsBuildData.yaml',
          JOB_ITEM_DEFAULTS: [
              buildFramework: 'ANT',
              scmBranch: 'dev'
          ],
          OVERRIDE_PARAMS:
              '''
              PARAM_TO_OVERRIDE: overriden
              BUILD_DATA_FILE: 'overrideParamsBuildData.yaml'
              JOB_ITEM_DEFAULTS :
                scmBranch: dev  
              ''',
          PARAM_TO_OVERRIDE: 'overriden'
      ]
  }

}
