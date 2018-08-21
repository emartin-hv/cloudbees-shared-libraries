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

package org.hitachivantara.ci.build

import org.hitachivantara.ci.JobItem
import org.hitachivantara.ci.build.impl.GradleBuilder
import org.hitachivantara.ci.build.impl.JenkinsJobBuilder
import org.hitachivantara.ci.build.impl.MavenBuilder
import org.hitachivantara.ci.build.impl.AntBuilder

class BuilderFactory implements Serializable {

  static IBuilder getBuildManager(final JobItem ji, Map builderData) {
    if (!ji.buildFramework) {
      throw new Exception("Build framework not configured")
    }

    def builder

    switch (ji.buildFramework) {
      case JobItem.BuildFramework.AUTO:
        // @TODO add custom handling for AUTO framework type, for now, default to Maven for Pentaho
      case JobItem.BuildFramework.MAVEN:
        builder = new MavenBuilder()
        break
      case JobItem.BuildFramework.ANT:
        builder = new AntBuilder()
        break
      case JobItem.BuildFramework.GRADLE:
        builder = new GradleBuilder()
        break
      case JobItem.BuildFramework.JENKINS_JOB:
        builder = new JenkinsJobBuilder()
        break
      default:
        throw new Exception("Sorry, I don't know how to handle the ${ji.buildFramework} type build framework.")
    }
    builder.setBuilderData(builderData)
    return builder
  }
}
