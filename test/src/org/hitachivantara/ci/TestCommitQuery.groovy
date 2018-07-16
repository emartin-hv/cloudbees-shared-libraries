package org.hitachivantara.ci

import org.hitachivantara.ci.config.BuildData
import org.hitachivantara.ci.config.BuildDataBuilder
import org.hitachivantara.ci.utils.MockDslScript
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.text.SimpleDateFormat

class TestCommitQuery extends Specification {
  @Shared Script script = new MockDslScript()

  @Unroll
  def "test commit query"() {
    setup:
      BuildData mbd = new BuildDataBuilder(script)
        .withBuildData('test/resources/thinBuildDataTestFile.yaml')
        .build()

      List allJobs = mbd.buildMap.collectMany { String jobGroup, List jobList -> jobList }
      Map scmLabeledJobs = allJobs.collectEntries { JobItem jobItem -> [(jobItem.scmLabel): jobItem] }

      String testDate = "2016-01-01 00:00:00"
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
      long ts = dateFormat.parse(testDate).getTime()
      GitHubApi mockedCodeRepo = Mock(GitHubApi.class) {
        1 * retrieveSha(orgRepo, _, ts) >> sha
      }

    when:
      CommitQuery commitQuery = new CommitQuery(checkoutTimestamp: testDate, codeRepo: mockedCodeRepo)
      commitQuery.retrieveTimestampShas(scmLabeledJobs)

    then:
      mbd.buildMap['3'][i].jobData.scmBranch == sha
      mbd.buildMap['3'][3].jobData.scmBranch == "8.0"

    and:
      noExceptionThrown()

    where:
      sha                                         | i | orgRepo
      'c76edff70377b5e16f7a22068594e4fdedcbf164'  | 1 | "webdetails/cgg"
      'da6edff70377b5e16f7a22068594e4fdedcbf234'  | 2 | "pentaho/pentaho-data-refinery"
  }
}
