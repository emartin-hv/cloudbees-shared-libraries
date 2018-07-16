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

import org.hitachivantara.ci.JobItem
import org.hitachivantara.ci.config.BuildData
import static org.hitachivantara.ci.config.BuildData.*

def call(BuildData mappedBuildData) {

  println "doing Push Changes..."

  Map buildProperties = mappedBuildData.getBuildProperties()

  Boolean ignoreFailures = buildProperties.getBool(IGNORE_PIPELINE_FAILURE)

  scmCredsID = buildProperties[CHECKOUT_CREDENTIALS_ID]
  noop = mappedBuildData.isNoop()

  //Generate checkouts map will return a map of maps that contain an array checkout closures
  // Iterate through the map and do one chunk of checkouts at a time

  mappedBuildData.getBuildMap().each { groupItem ->
    def jobItems = groupItem.getValue()
    if (jobItems.size() > 1) {
      def parallelJobs = [:]
      jobItems.each { jobItem ->
        JobItem ji = (JobItem)jobItem
        parallelJobs.put(ji.getJobID(), getPushChangesClosure(ji))
      }
      // This doesn't have the look we want, but is functional
      println "Running parallel job group ${groupItem.getKey()}"

      parallelJobs.failFast = !ignoreFailures
      parallel parallelJobs

    } else {
      //single job closure
      JobItem ji = (JobItem)jobItems[0]
      println "Running single job ${ji.getJobID()}"

      // This doesn't have the look we want, but is functional
      // At least here the log prefixes the [jobId] before each log line
      parallel([(ji.getJobID()): getPushChangesClosure(ji), failFast: !ignoreFailures])
    }
  }
}

/**
 *
 * @param JobItem
 * @return
 */
def getPushChangesClosure(JobItem ji) {

    Closure cls

    if (noop || ji.isExecNoop()) {
      cls = {
        println "NoOp: branch: ${ji.getScmBranch()}"
      }
    } else {
      cls = {
        println "Pushing changes for ${ji.getJobID()}"
        dir("${ji.getBuildWorkDir()}") {
          withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${scmCredsID}", 
                            usernameVariable: 'GITUSER', passwordVariable: 'PASSWORD']])  {
            sh  """ if ! git diff-index --diff-filter=M --quiet HEAD
                    then
                      echo "committing version merger updates ..."
                      git config user.name $GITUSER
                      git config user.email $GITUSER
                      git checkout ${ji.getScmBranch()}
                      git ls-files --modified | grep 'build.properties' | xargs git add
                      git ls-files --modified | grep 'assembly.properties' | xargs git add
                      git ls-files --modified | grep 'manual_assembly.properties' | xargs git add
                      git ls-files --modified | grep 'dev_build.properties' | xargs git add
                      git ls-files --modified | grep 'publish.properties' | xargs git add
                      git ls-files --modified | grep 'pom.xml' | xargs git add
                      git commit -v --untracked-files=no -m "[CLEANUP] updated versions via release version merger"
                      git remote set-url origin https://$GITUSER:$PASSWORD@${ji.getScmUrl().replaceFirst("^(?i)http[s]?://","")}
                      git push origin ${ji.getScmBranch()}
                      echo "version merger updates pushed to ${ji.getScmBranch()} branch"
                    else
                      echo "no changes found"
                    fi
                    exit 0 """
          }
        }
      }  
    }  

    return cls
}
