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

  println "doing tags..."
  Map buildProperties = mappedBuildData.getBuildProperties()
  Boolean ignoreFailures = buildProperties.getBool(IGNORE_PIPELINE_FAILURE)

  //Generate checkouts map will return a map of maps that contain an array checkout closures
  // Iterate through the map and do one chunk of checkouts at a time

  mappedBuildData.getBuildMap().each { groupItem ->
    def jobItems = groupItem.getValue()
    if (jobItems.size() > 1) {
      def parallelJobs = [:]
      jobItems.each { jobItem ->
        JobItem ji = (JobItem)jobItem
        parallelJobs.put(ji.getJobID(), getDoTagClosure(ji, mappedBuildData))
      }
      // This doesn't have the look we want, but is functional
      println "Running parallel job group ${groupItem.getKey()}"

      parallelJobs.failfast = !ignoreFailures
      parallel parallelJobs

    } else {
      //single job closure
      JobItem ji = (JobItem)jobItems[0]
      println "Running single job ${ji.getJobID()}"

      // This doesn't have the look we want, but is functional
      // At least here the log prefixes the [jobId] before each log line
      parallel([(ji.getJobID()): getDoTagClosure(ji, mappedBuildData), failFast: !ignoreFailures])
    }
  }
}

/**
 *
 * @param JobItem
 * @return
 */
def getDoTagClosure(JobItem ji, BuildData buildData) {

    Closure cls

    Map buildProperties = buildData.getBuildProperties()
    String scmCredsID = buildProperties[CHECKOUT_CREDENTIALS_ID]

    String DEFAULT_TAG_CREATE_MESSAGE = "Create tag from build job " + buildProperties['JOB_NAME'] + " " + currentBuild.displayName + ", source branch ${ji.getScmBranch()}"

    Map tagData = getTagData(buildData)

    if (buildData.isNoop() || ji.isExecNoop() || !tagData['tagSuccessfulBuild']) {
      cls = {
        println "NoOp: branch: ${ji.getScmBranch()}"
      }
    } else {
      cls = {
        println "Tag for ${ji.getJobID()}"
        dir("${ji.getBuildWorkDir()}") {
          withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${scmCredsID}",
                            usernameVariable: 'GITUSER', passwordVariable: 'PASSWORD']])  {
            sh  """ if [ -z "${tagData['tagVersion']}" ] 
                    then
                      echo "tagVersion is unset. Fix it!"
                      exit 1
                    fi

                    git config user.name $GITUSER
                    git config user.email $GITUSER
                    git remote set-url origin https://$GITUSER:$PASSWORD@${ji.getScmUrl().replaceFirst("^(?i)http[s]?://","")}

                    # Don't tag if the version contains SNAPSHOT
                    if [ "${tagData['tagVersion']}" == *SNAPSHOT* ]
                    then
                      echo "Not tagging snapshot version" ${tagData['tagVersion']}
                      exit 0
                    fi

                    # If tag does not exist, the next two command will fail
                    # Use `set +e` so this won't fail the job
                    set +e

                    # Delete local release tag if it already exists
                    git tag -d ${tagData['tagVersion']}

                    # Delete remote release tag if it already exists
                    git push origin :refs/tags/${tagData['tagVersion']}

                    # Commit our local changes
                    # If no changes this command will fail
                    git commit -v --untracked-files=no -a -m "${DEFAULT_TAG_CREATE_MESSAGE}"

                    # If tag create or push to remote fails, fail the job
                    set -e

                    # Create a new local tag including local changes
                    git tag -a ${tagData['tagVersion']} -m "${DEFAULT_TAG_CREATE_MESSAGE}"

                    # Push the new tag to remote repository
                    git push origin ${tagData['tagVersion']} """
          }
        }
      }
    }

    return cls
}

def getTagData(BuildData buildData) {

  //Deal with tagging - default to false
  boolean tagSuccessfulBuild = false
  String tagVersion

  String tagNameType = buildData.getTagNameType()
  String tagName = buildData.getTagName()
  String version = buildData.getReleaseVersion()

  if (!tagNameType || tagNameType == TagNameType.NONE.toString()) {
    tagVersion = ""
    tagSuccessfulBuild = false
    println "TAG_NAME_TYPE is NONE, not tagging."
  } else if (tagNameType == TagNameType.FULL.toString()) {
    tagVersion = tagName
    tagSuccessfulBuild = true
  } else if (tagNameType == TagNameType.PREFIX.toString()) {
    tagVersion = tagName + version
    tagSuccessfulBuild = true
  } else if (tagNameType == TagNameType.SUFFIX.toString()) {
    tagVersion = version + tagName
    tagSuccessfulBuild = true
  }

  return [
    'tagSuccessfulBuild' : tagSuccessfulBuild,
    'tagVersion' : tagVersion
  ]

}

/*
 * Tag name can be:
 * NONE = Do not tag
 * FULL = Use the defined tag name in total for the tag name
 * PREFIX = Use tag name as a prefix to the project version we are building
 * SUFFIX = Use tag name as a suffix to the project version we are building
 * Based on the way we have been tagging so far '8.2.0.0-R' the tag type would be SUFFIX and the tag name
 * would be "-R"
 */

enum TagNameType {
  NONE, FULL, PREFIX, SUFFIX
}
