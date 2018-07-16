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

package org.hitachivantara.ci.utils

class ClosureInspectionHelper {
  def str = ""<<""
  def i = 0
  def cmds = []
  def workdir = []
  def delegate

  void sh(cmd) {
    cmds << cmd
    str << ' '*(i*2)
    str << "sh $cmd" << '\n'
  }

  void dir(cmd, args) {
    workdir << cmd
    str << ' '*(i*2)
    str << "dir (\"$cmd\")" << '\n'
    str << ' '*(i++*2) <<'{\n'
    args()
    str << ' '*(--i*2) << '}\n'
  }

  def propertyMissing(String name) {
    if (delegate) {
      def val = delegate[name]
      return val instanceof String ? val : this
    }
    return this
  }

  def methodMissing(String methodName, args) {
    str << ' '*(i*2)
    str << methodName
    args.each {
      switch(it) {
        case Closure:
          str << ' '*(i++*2) <<'{\n'
          it()
          str << ' '*(--i*2) << '}\n'
          break
        case GString:
          str << ' ("'
          if (!it.values.any { it instanceof ClosureInspectionHelper }) {
            str << it
          }
          str << '") ' << '\n'
          break
        default:
          str << ' (' << it.toString() << ') ' << '\n'
      }
    }
    return
  }

  String toString() {
    str
  }
}
