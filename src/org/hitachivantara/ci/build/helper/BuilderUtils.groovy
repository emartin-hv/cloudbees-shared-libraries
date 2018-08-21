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

package org.hitachivantara.ci.build.helper

import com.cloudbees.groovy.cps.NonCPS
import hudson.AbortException
import hudson.model.Result
import hudson.model.Run
import hudson.scm.ChangeLogSet
import jenkins.model.CauseOfInterruption
import org.hitachivantara.ci.JobItem
import org.hitachivantara.ci.config.BuildData
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

import java.util.Map.Entry
import java.util.regex.Matcher
import java.util.regex.Pattern

class BuilderUtils {

  final static String SPACER = ' '
  final static String ADDITIVE_EXPR = '+='
  final static String SUBTRACTIVE_EXPR = '-='

  static DirectivesData parseDirectivesData(final String configuredDirectives) {

    if (!configuredDirectives) {
      return null
    }

    if (configuredDirectives.split("\\${ADDITIVE_EXPR}", -1).length - 1 > 1 ||
      configuredDirectives.split("${SUBTRACTIVE_EXPR}", -1).length - 1 > 1) {
      throw new Exception("Only one occurrence of each of the chars '${ADDITIVE_EXPR}' and '${SUBTRACTIVE_EXPR}' is permitted ['${configuredDirectives}']")
    }

    def addPosition = configuredDirectives.indexOf(ADDITIVE_EXPR)
    def subPosition = configuredDirectives.indexOf(SUBTRACTIVE_EXPR)

    if (addPosition == -1 && subPosition == -1) {
      return null
    }

    DirectivesData directivesData = new DirectivesData()

    if (addPosition > -1) {
      directivesData.additive = getDirective(configuredDirectives, "\\${ADDITIVE_EXPR}.*?${SUBTRACTIVE_EXPR}")

      if (!directivesData.additive) {
        directivesData.additive = configuredDirectives.substring(addPosition + ADDITIVE_EXPR.length(), configuredDirectives.length()).trim()
      }
      directivesData.additive += SPACER
    }

    if (subPosition > -1) {
      directivesData.subtractive = getDirective(configuredDirectives, "${SUBTRACTIVE_EXPR}.*?\\${ADDITIVE_EXPR}")

      if (!directivesData.subtractive) {
        directivesData.subtractive = configuredDirectives.substring(subPosition + SUBTRACTIVE_EXPR.length(), configuredDirectives.length()).trim()
      }
      directivesData.subtractive += SPACER
    }

    return directivesData
  }

  static String getDirective(String configuredDirectives, final String regExpr) {
    Pattern p = Pattern.compile(regExpr)
    Matcher m = p.matcher(configuredDirectives)

    if (m.find())
      return m.group().subSequence(ADDITIVE_EXPR.length(), m.group().length() - ADDITIVE_EXPR.length()).toString().trim()
    return null
  }

  /**
   * If job directives exist:
   *   Replace or merge default directive with job directive
   * If no job directives, just use the data default directives
   *
   * Additional custom directives that are specific to the build framework will be appended by the caller
   *
   * @param self
   * @param defaultDirectives
   * @param directives
   * @return
   */
  static <T> T applyBuildDirectives(T self, String defaultDirectives, String directives) {
    if (directives?.empty) {
      self << ' ' << defaultDirectives
      return self
    }

    DirectivesData directivesData = parseDirectivesData(directives)
    if (directivesData) {
      if (defaultDirectives) self << ' ' << defaultDirectives
      if (directivesData.additive) self << ' ' << directivesData.additive.trim()
      //TODO: use a proper command object that validates it's input instead of a StringBuilder
      if (self instanceof StringBuilder) {
        String regex = directivesData.subtractive?.split()?.join('|')
        replaceAll(self, ~/(?i) ($regex)/, '')
      } else {
        self -= directivesData.subtractive
      }
      return self
    }

    //TODO: use a proper command object that validates it's input instead of a StringBuilder
    //override defaults with job directives if they exist
    if (self instanceof StringBuilder) {
      String regex = defaultDirectives?.split()?.join('|')
      replaceAll(self, ~/(?i)\s?($regex)\s?/, '')
    } else {
      self -= defaultDirectives
    }
    self << ' ' << directives
    return self
  }


  static StringBuilder replaceAll(StringBuilder self, Pattern pattern, String replacement) {
    Matcher m = pattern.matcher(self)
    int start = 0
    while (m.find(start)) {
      self.replace(m.start(), m.end(), replacement)
      start = m.start() + replacement.size()
    }
    return self
  }

  /**
   * Find the closest build file to the given file
   * @param base
   * @param current
   * @return
   */
  static File findBuildFile(File base, File current, String buildFilename) {
    /*
     * Search the for the closest build file to the given file
     * Location: a/build.xml
     * Iterations: a/b/c/version.xml -> a/b/c/ -> a/b/ -> a/
     */

    if (!current.exists() || base == current) {
      // file doesn't belong to this tree, give up.
      return null
    }
    if (current.directory) {
      // Does the build file exist under current dir?
      File file = new File(current, buildFilename)
      if (file.exists()) {
        return file
      }
    } else if (current.name == buildFilename) {
      return current
    }

    // Keep searching up
    findBuildFile(base, current.parentFile, buildFilename)
  }

  static String getRelativePath(String path, String base) {
    path - (base + File.separatorChar)
  }

  /**
   * Given a JobItem, it will update the remaining groups items to FORCE
   * @param jobItem
   * @param buildMap
   */
  static void forceRemainingJobItems(JobItem jobItem, LinkedHashMap<String, List<JobItem>> buildMap) {
    boolean start = false
    for (Entry<String,List<JobItem>> entry in buildMap) {
      String grp = entry.getKey()
      if (grp == jobItem.jobGroup) {
        start = true
        continue
      }
      if (start) {
        List<JobItem> jobs = entry.getValue()
        jobs.each { JobItem job ->
          if (!job.execNoop) {
            job.execType = JobItem.ExecutionType.FORCE
          }
        }
      }
    }
  }

  /**
   * execute the shell command, but handle the exit code for proper logging
   * @param String cmd
   * @param Script dsl
   */
  static void process(String cmd, Script dsl) throws Exception {
    int exitCode = dsl.sh(returnStatus:true, script:cmd)
    if (exitCode != 0) {
      int signal = getErrorSignal(exitCode)
      if (signal == 15) {
        // someone killed this process, could have been caused by a failFast error or a user aborted the job.
        throw new FlowInterruptedException(Result.NOT_BUILT, new CauseOfInterruption() {
          @Override
          String getShortDescription() {
            return 'Job item was terminated: SIGTERM'
          }
        })
      }
      throw new AbortException("script returned exit code $exitCode")
    }
  }

  static int getExitCode(String cause) {
    def matcher = (cause =~ /script returned exit code (\d+)/)
    def exitCode = 0
    if (matcher.matches() && matcher.hasGroup()) {
      exitCode = matcher.group(1).toInteger()
    }
    return exitCode
  }

  /**
   * list of known reserved exit codes http://www.tldp.org/LDP/abs/html/exitcodes.html
   * 1       SIGHUP (hang up)
   * 2       SIGINT (interrupt)
   * 3       SIGQUIT (quit)
   * 6       SIGABRT (abort)
   * 9       SIGKILL (non-catchable, non-ignorable kill)
   * 14      SIGALRM (alarm clock)
   * 15      SIGTERM (software termination signal)
   *
   * @param exitCode
   * @return
   */
  static int getErrorSignal(int exitCode) {
    // 128+n  --> Fatal error signal "n"
    if (exitCode > 128 && exitCode <= 143) return exitCode ^ 128
    return 0
  }

  @NonCPS
  static <T> List<T> intersect(List<T> listA, List<T> listB) {
    if (listB) {
      return listA.intersect(listB)
    }
    return listA
  }

  /**
   * If previous success build is null, then this is the first build or there are no previous successful builds,
   * return null to trigger a new build.
   * If changes are an empty list, then nothing changed, return empty list to skip this build
   *
   * @return null if no successful builds, List otherwise
   */
  @NonCPS
  static List<String> getChangeSet(Script dsl) {
    // find the latest successful build and get the change list
    List<String> changeSet = []
    def build = dsl.currentBuild.rawBuild
    Run r = build.previousSuccessfulBuild

    if (!r) return null

    while (build?.number > r.number) {
      build.changeSets.each { ChangeLogSet log ->
        log.items.each { ChangeLogSet.Entry entry ->
          changeSet += entry.affectedPaths
        }
      }
      build = build.previousBuild
    }
    return changeSet
  }

  /**
   * If this job can be skipped
   * @param jobItem
   * @return Closure that does nothing, null otherwise
   */
  static Closure canBeSkipped(JobItem jobItem, BuildData buildData, Script dsl, boolean testPhase = false) {
    if (buildData.noop || jobItem.isExecNoop()) {
      return { -> dsl.log.info "${jobItem.jobID}: NOOP so not building ${jobItem.scmLabel}" }
    }
    if (testPhase && !jobItem.testable) {
      return { -> dsl.log.info "${jobItem.jobID}: skipped ${jobItem.scmLabel} (not testable)" }
    }
    if (jobItem.isExecAuto() && getChangeSet(dsl)?.empty) {
      return { -> dsl.log.info "${jobItem.jobID}: skipped ${jobItem.scmLabel} (no changes)" }
    }
    return null
  }

  static List<List<?>> organizeItems(List<?> items) {
    if (!items) return items
    def result = []
    def remaining = []
    items.each {
      switch (it) {
        case List:
          result += it.remove(0)
          if (it) remaining << it
          break
        default:
          result << it
      }
    }
    return [result] + organizeItems(remaining)
  }
}
