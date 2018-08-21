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

package org.hitachivantara.ci.build.impl

import org.hitachivantara.ci.JobItem
import org.hitachivantara.ci.build.IBuilder
import org.hitachivantara.ci.build.helper.BuilderUtils
import org.hitachivantara.ci.config.BuildData

import static org.hitachivantara.ci.config.BuildData.*

class AntBuilder implements IBuilder {

  private final static String BASE_COMMAND = "ant"

  private BuildData buildData
  private Script dsl

  void setBuilderData(Map builderData) {
    this.buildData = builderData['buildData']
    this.dsl = builderData['dsl']
  }

  Closure getBuildClosure(JobItem jobItem) {
    if (buildData.noop || jobItem.isExecNoop()) {
      return { -> dsl.echo "${jobItem.getJobID()} NOOP so not building ${jobItem.getScmLabel()}" }
    }

    Map buildProperties = buildData.getBuildProperties()
    String ivyLocalRepoPath = "${buildProperties[WORKSPACE] ?: ''}/caches/.ivy2"
    String defaultAntOpts = buildProperties[ANT_DEFAULT_COMMAND_OPTIONS]

    StringBuilder antCmd = new StringBuilder()
    antCmd << BASE_COMMAND
    if (defaultAntOpts) {
      antCmd << "  ${buildProperties[ANT_DEFAULT_COMMAND_OPTIONS]}"
    }
    antCmd << " -Divy.default.ivy.user.dir=${ivyLocalRepoPath}"

    if (jobItem.buildFile) {
      antCmd << " -buildfile ${jobItem.buildFile}"
    }

    BuilderUtils.applyBuildDirectives(antCmd, buildProperties[ANT_DEFAULT_DIRECTIVES] as String, jobItem.directives)
    String testTargets = buildData.buildProperties[ANT_TEST_TARGETS] ?: ''

    if (!testTargets.empty) {
      String forbidden = testTargets.split().join('|')
      antCmd.replaceAll(~/(?i)\s?($forbidden)\s?/, '')
    }

    dsl.echo "Ant build directives for ${jobItem.getJobID()}: ${antCmd}"

    return getAntDsl(jobItem, antCmd.toString())
  }

  Closure getTestClosure(JobItem jobItem) {
    if (buildData.noop || jobItem.isExecNoop() || !jobItem.testable) {
      return { -> dsl.echo "${jobItem.jobID}: skipped testing ${jobItem.scmLabel}" }
    }

    Map buildProperties = buildData.getBuildProperties()

    StringBuilder antCmd = new StringBuilder()
    antCmd << BASE_COMMAND
    if (jobItem.buildFile) {
      antCmd << " -buildfile ${jobItem.buildFile}"
    }

    String testTargets = buildData.buildProperties[ANT_TEST_TARGETS] ?: ''
    if (!testTargets.empty) {
      antCmd << " $testTargets"
    }

    BuilderUtils.applyBuildDirectives(antCmd, buildProperties[ANT_DEFAULT_DIRECTIVES] as String, jobItem.directives)

    dsl.echo "Ant unit test build directives for ${jobItem.getJobID()}: ${antCmd} (testable=${jobItem.testable})"

    return getAntDsl(jobItem, antCmd.toString())
  }

  List<List<JobItem>> expandWorkItem(JobItem jobItem) {
    dsl.log.warn "Expanding jobItem not implemented for Ant, reverting to normal"
    [[jobItem]]
  }

  private Closure getAntDsl(JobItem jobItem, String antCmd) {
    Map buildProperties = buildData.getBuildProperties()

    return { ->
      dsl.dir(jobItem.buildWorkDir) {
        dsl.withEnv(["PATH+MAVEN=${dsl.tool "${buildProperties[JENKINS_MAVEN_FOR_BUILDS]}"}/bin"]) {
          dsl.withAnt(
              installation: "${buildProperties[JENKINS_ANT_FOR_BUILDS]}",
              jdk: "${buildProperties[JENKINS_JDK_FOR_BUILDS]}",
          ) {
            BuilderUtils.process(antCmd, dsl)
          }
        }
      }
    }
  }
}
