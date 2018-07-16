@Grapes([
        @GrabConfig(systemClassLoader = true),
        @GrabResolver(name = 'pentaho', root = 'http://nexus.pentaho.org/content/groups/omni'),
        @Grab('org.kohsuke:github-api:1.92'),
        @Grab('org.yaml:snakeyaml:1.19')
])

import org.hitachivantara.ci.CommitQuery
import org.hitachivantara.ci.config.BuildData

class TestGitHub {
    static Map env

    static BuildData setupMappedData(String fileName) {
        println "loading file"

        BuildData mbd = BuildData.build('../../../test/resources/thinBuildDataTestFile.yaml')
        return mbd
    }

    static printCheckoutsMap(jobScmMap) {
        def keysArray = jobScmMap.keySet().toArray()
        println "SCM Checkouts map: "
        for (int j = 0; j < jobScmMap.size(); j++) {
            JobItem jobItem = jobScmMap.get(keysArray[j])
            println "${jobItem.getJobID()} : ${jobItem.getScmBranch()}"
        }
    }

    static void testApi() {
        def params = [CHECKOUT_TIMESTAMP: "2014-01-01 00:00:00"]

        BuildData mbd = setupMappedData()
        List allJobs = mbd.buildMap.collectMany { String jobGroup, List jobList -> jobList }
        Map scmLabeledJobs = allJobs.collectEntries { JobItem jobItem -> [(jobItem.scmLabel): jobItem] }

        GitHubApi codeRepo = new GitHubApi(gitUser: env.GIT_USER, gitPass: env.GIT_PASS)
        CommitQuery commitQuery = new CommitQuery(checkoutTimestamp: params.CHECKOUT_TIMESTAMP, codeRepo: codeRepo)
        commitQuery.retrieveTimestampShas(scmLabeledJobs)
        println "done"
        printCheckoutsMap(scmLabeledJobs)
    }

    static void testCurrentTimestamp() {
        def params = [CHECKOUT_TIMESTAMP: "CURRENT_TIME"]

        BuildData mbd = setupMappedData()
        List allJobs = mbd.buildMap.collectMany { String jobGroup, List jobList -> jobList }
        Map scmLabeledJobs = allJobs.collectEntries { JobItem jobItem -> [(jobItem.scmLabel): jobItem] }

        GitHubApi gitHubApi = new GitHubApi(checkoutTimestamp: params.CHECKOUT_TIMESTAMP, gitUser: env.GIT_USER, gitPass: env.GIT_PASS)
        gitHubApi.retrieveTimestampShas(scmLabeledJobs)
        println "done"
        printCheckoutsMap(scmLabeledJobs)
    }

    void run() {
        this.env = [GIT_USER: System.getenv('GIT_USER'), GIT_PASS: System.getenv('GIT_PASS')]
        testApi()
//        testCurrentTimestamp()
    }
}

new TestGitHub().run()