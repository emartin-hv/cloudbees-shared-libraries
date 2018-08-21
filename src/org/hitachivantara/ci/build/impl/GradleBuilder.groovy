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

class GradleBuilder implements IBuilder {

  private final static String BASE_COMMAND = "gradle"
  private BuildData buildData
  private Script dsl

  void setBuilderData(Map builderData) {
    this.buildData = builderData['buildData']
    this.dsl = builderData['dsl']
  }

  Closure getBuildClosure(JobItem jobItem) {

    if (buildData.noop || jobItem.execNoop) {
      return { -> dsl.echo "${jobItem.getJobID()} NOOP so not building ${jobItem.getScmLabel()}" }
    }

    Map buildProperties = buildData.getBuildProperties()
    String gradleLocalRepoPath = "${buildProperties[WORKSPACE] ?: ''}/caches/.gradle"
    String defaultGradleOpts = buildProperties[GRADLE_DEFAULT_COMMAND_OPTIONS]

    StringBuilder gradleCmd = new StringBuilder(BASE_COMMAND)

    if (defaultGradleOpts) {
      gradleCmd << " ${buildProperties[GRADLE_DEFAULT_COMMAND_OPTIONS]}"
    }

    if (jobItem.buildFile) {
      gradleCmd << " -b ${jobItem.buildFile}"
    }

    if (jobItem.settingsFile) {
      gradleCmd << " -c ${jobItem.settingsFile}"
    }

    BuilderUtils.applyBuildDirectives(gradleCmd, buildProperties[GRADLE_DEFAULT_DIRECTIVES] as String, jobItem.directives)
    String testTargets = buildData.buildProperties[GRADLE_TEST_TARGETS] ?: ''

    gradleCmd << " --gradle-user-home=${gradleLocalRepoPath}"

    if (!testTargets.empty) {
      gradleCmd << " -x "
      gradleCmd << testTargets
    }

    dsl.echo "Gradle build directives for ${jobItem.getJobID()}: ${gradleCmd}"

    return getGradleDsl(jobItem, gradleCmd.toString())

  }

  Closure getTestClosure(JobItem jobItem) {

    if (buildData.noop || jobItem.execNoop || !jobItem.testable) {
      return { -> dsl.echo "${jobItem.jobID}: skipped testing ${jobItem.scmLabel}" }
    }

    String testTargets = buildData.buildProperties[GRADLE_TEST_TARGETS] ?: ''

    if (!testTargets.empty) {
      StringBuilder gradleCmd = new StringBuilder(BASE_COMMAND)
      gradleCmd << ' '

      if (jobItem.buildFile) {
        gradleCmd << "-b ${jobItem.buildFile}"
        gradleCmd << ' '
      }

      if (jobItem.settingsFile) {
        gradleCmd << "-c ${jobItem.settingsFile}"
        gradleCmd << ' '
      }

      gradleCmd << testTargets
      gradleCmd << ' '

      BuilderUtils.applyBuildDirectives(gradleCmd, buildData.buildProperties[GRADLE_DEFAULT_DIRECTIVES] as String, jobItem.directives)

      String forbidden = ['clean', 'build'].join('|')
      gradleCmd = new StringBuilder(gradleCmd.replaceAll(~/(?i)\s?($forbidden)\s?/, ''))

      return getGradleDsl(jobItem, gradleCmd.toString())

    } else {
      return { -> dsl.echo "${jobItem.jobID}: no test targets defined" }

    }
  }

  List<List<JobItem>> expandWorkItem(JobItem jobItem) {
    dsl.log.warn "Expanding jobItem not implemented for Gradle, reverting to normal"
    [[jobItem]]
  }

  Closure getGradleDsl(JobItem jobItem, String gradleCmd) {
    Map buildProperties = buildData.getBuildProperties()

    return { ->
      dsl.dir(jobItem.buildWorkDir) {
        dsl.withEnv(["PATH+GRADLE=${dsl.tool "${buildProperties[JENKINS_GRADLE_FOR_BUILDS]}"}/bin",
                     "JAVA_HOME=${dsl.tool "${buildProperties[JENKINS_JDK_FOR_BUILDS]}"}"]) {
          BuilderUtils.process(gradleCmd, dsl)
        }
      }
    }

  }

}
