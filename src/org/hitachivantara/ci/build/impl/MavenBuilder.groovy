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

import com.cloudbees.groovy.cps.NonCPS
import hudson.model.Run
import hudson.scm.ChangeLogSet
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Option
import org.apache.maven.cli.CLIManager
import org.hitachivantara.ci.JobItem
import org.hitachivantara.ci.build.IBuilder
import org.hitachivantara.ci.build.helper.BuilderUtils
import org.hitachivantara.ci.build.tools.maven.MavenModule
import org.hitachivantara.ci.config.BuildData

import java.nio.file.Path

import static org.hitachivantara.ci.config.BuildData.*

/*
NOTE: NonCPS annotated methods cannot call CPS transformed code!
Take that in consideration when annotating something with NonCPS.
 */

class MavenBuilder implements IBuilder, Serializable {

  final static String BASE_COMMAND = "mvn"

  private BuildData buildData
  private Script dsl

  @Override
  void setBuilderData(Map builderData) {
    this.buildData = builderData['buildData']
    this.dsl = builderData['dsl']
  }

  Closure getBuildClosure(JobItem jobItem) {
    Closure skip = canBeSkipped(jobItem)
    if (skip) return skip

    Map buildProperties = buildData.getBuildProperties()

    CommandBuilder command = new CommandBuilder()
    command += buildProperties[MAVEN_DEFAULT_COMMAND_OPTIONS]
    if (jobItem.buildFile) {
      command += "-f ${jobItem.buildFile}"
    }
    command += '-DskipTests'
    // This is for using Takari Concurrent Local Repository which uses aether so to avoid the occasional .part file
    // 'resume' (see: https://github.com/takari/takari-local-repository/issues/4) issue we send this:
    command += '-Daether.connector.resumeDownloads=false'
    BuilderUtils.applyBuildDirectives(command, buildProperties[MAVEN_DEFAULT_DIRECTIVES] as String, jobItem.directives)

    if (jobItem.isExecAuto()) {
      if (!changeProjectList(command, jobItem)) {
        // in case that the change detection has confirmed that changes doesn't belong to this job item
        return { -> dsl.echo "${jobItem.jobID}: skipped ${jobItem.scmLabel} (no changes)" }
      }
      // change the remaining groups to FORCE
      // TODO: use some kind of dependency graph to change only the affected downstreams
      BuilderUtils.forceRemainingJobItems(jobItem, buildData.buildMap)
    }

    String mvnCommand = command.build()
    dsl.echo "Maven build directives for ${jobItem.getJobID()}: $mvnCommand"
    return getMvnDsl(jobItem, mvnCommand)
  }

  Closure getTestClosure(JobItem jobItem) {
    def skip = canBeSkipped(jobItem, true)
    if (skip) return skip

    Map buildProperties = buildData.getBuildProperties()

    CommandBuilder command = new CommandBuilder()
    command += 'test'
    command += buildProperties[MAVEN_DEFAULT_COMMAND_OPTIONS]
    if (jobItem.buildFile) {
      command += "-f ${jobItem.buildFile}"
    }
    command += buildProperties[MAVEN_TEST_OPTS]
    // This is for using Takari Concurrent Local Repository which uses aether so to avoid the occasional .part file
    // 'resume' (see: https://github.com/takari/takari-local-repository/issues/4) issue we send this:
    command += '-Daether.connector.resumeDownloads=false'
    BuilderUtils.applyBuildDirectives(command, buildProperties[MAVEN_DEFAULT_DIRECTIVES] as String, jobItem.directives)

    // list of goals that we want stripped from the final command
    command -= ['clean', 'validate', 'compile', 'verify', 'package', 'install', 'deploy', '-Dmaven.test.skip'].join(' ')

    if (jobItem.isExecAuto()) {
      if (!changeProjectList(command, jobItem)) {
        // in case that the change detection has confirmed that changes doesn't belong to this job item
        return { -> dsl.echo "${jobItem.jobID}: skipped ${jobItem.scmLabel} (no changes)" }
      }
    }

    String mvnCommand = command.build()
    dsl.echo "Maven unit test build directives for ${jobItem.jobID}: ${mvnCommand} (testable=${jobItem.testable})"
    return getMvnDsl(jobItem, mvnCommand)
  }

  private Closure getMvnDsl(JobItem jobItem, String cmd) {
    Map buildProperties = buildData.getBuildProperties()

    String mavenSettingsFile = "${buildProperties.getString(WORKSPACE)}/resources/config/jenkins-pipeline-settings1.xml"
    String globalMavenSettingsFile = "${buildProperties.getString(WORKSPACE)}/resources/config/jenkins-pipeline-settings1.xml"
    String cacheRoot = buildProperties.getString(LIB_CACHE_ROOT_PATH)
    String mavenLocalRepoPath = cacheRoot == null ? "${buildProperties.getString(WORKSPACE)}/caches/.m2/repository" : "${cacheRoot}/.m2/repository"

    return { ->
      dsl.dir(jobItem.buildWorkDir) {
        dsl.withEnv(["RESOLVE_REPO_MIRROR=${buildProperties.MAVEN_RESOLVE_REPO_URL}"]) {
          dsl.withMaven(
              mavenSettingsFilePath: mavenSettingsFile,
              globalMavenSettingsFilePath: globalMavenSettingsFile,
              jdk: buildProperties.getString(JENKINS_JDK_FOR_BUILDS),
              maven: buildProperties.getString(JENKINS_MAVEN_FOR_BUILDS),
              mavenLocalRepo: mavenLocalRepoPath,
              mavenOpts: buildProperties.getString(MAVEN_OPTS),
              publisherStrategy: 'EXPLICIT') {
            // And here's the build command!
            dsl.sh cmd
          }
        }
      }
    }
  }

  /**
   * if this job can be skipped
   * @param jobItem
   * @return Closure that does nothing, null otherwise
   */
  Closure canBeSkipped(JobItem jobItem, boolean testPhase = false) {
    if (buildData.noop || jobItem.isExecNoop()) {
      return { -> dsl.echo "${jobItem.jobID}: NOOP so not building ${jobItem.scmLabel}" }
    }
    if (testPhase && !jobItem.testable) {
      return { -> dsl.echo "${jobItem.jobID}: skipped ${jobItem.scmLabel} (not testable)" }
    }
    if (jobItem.isExecAuto() && changeSet?.empty) {
      return { -> dsl.echo "${jobItem.jobID}: skipped ${jobItem.scmLabel} (no changes)" }
    }
    return null
  }

  /**
   * This will update the command to best accommodate the detected scm changes.
   * Also, if any change doesn't belong to the initial reactor tree, that change is not included.
   * @param command
   * @param jobItem
   * @return true if the command can be evaluated, false otherwise
   */
  boolean changeProjectList(CommandBuilder command, JobItem jobItem) {
    List<String> changes = getChangeSet()
    if (!changes) {
      // if changes is null, then this is the first build or there are no previous successful builds, return true to trigger a new build
      // if changes is an empty list, that means nothing changed, return false to skip this build
      return changes == null
    }

    // remove any alternative pom file if present
    command.removeOption('-f')

    List<String> activeProfiles = command.getActiveProfileIds()
    List<String> projectList = command.getProjectList()
    Properties properties = command.getUserProperties()

    // We wan't to manually define the project list,
    // we can't remove Profiles because we can't control their activation condition
    command.removeOption('-pl')

    File repo = new File(jobItem.buildWorkDir)
    File rootPom = new File(repo, jobItem.buildFile ?: 'pom.xml')
    MavenModule rootModule = MavenModule.buildModule(rootPom)
    List<String> activeModules = MavenModule.activeModules(rootModule, activeProfiles, properties)
    if (rootModule.fullPath != '.') {
      activeModules = activeModules.collect { String m -> "${rootModule.fullPath}/$m".toString() }
      activeModules << rootModule.fullPath
    }

    Set<MavenModule> modules = []
    for (String changedFile in changes) {
      File workspace = new File(jobItem.checkoutDir)
      Path workDir = new File(jobItem.buildWorkDir).toPath()
      File file = new File(workspace, changedFile)

      if (jobItem.root && !file.toPath().startsWith(workDir)) continue

      File pom = BuilderUtils.findBuildFile(repo, file, 'pom.xml')
      if (pom) {
        modules << MavenModule.buildModule(pom)
      }
    }

    if (!activeModules) {
      // we are at a leaf module, check if it intersects with the change set
      if (modules.contains(rootModule)) {
        command << "-f ${BuilderUtils.getRelativePath(rootModule.pom.canonicalPath, repo.canonicalPath)}"
        command << '-amd'
        return true
      }
    } else {
      activeModules = intersect(activeModules, projectList)

      // grab the list of changed modules, and filter only the active ones
      List<MavenModule> filteredModules = filterByActiveModules(modules, activeModules)
      if (filteredModules) {
        command << "-f ${BuilderUtils.getRelativePath(rootModule.pom.canonicalPath, repo.canonicalPath)}"
        String list = sortProjectList(filteredModules, rootModule)
        if (list) {
          command << "-pl '${list}'"
        }
        command << '-amd'
        return true
      }
    }
    return false
  }

  @NonCPS
  private <T> List<T> intersect(List<T> listA, List<T> listB) {
    if (listB) {
      return listA.intersect(listB)
    }
    return listA
  }

  @NonCPS
  private List<MavenModule> filterByActiveModules(Set<MavenModule> modules, List<String> activeModules) {
    modules.findAll { MavenModule m ->
      return activeModules.contains(m.fullPath)
    } as List
  }

  @NonCPS
  private String sortProjectList(List<MavenModule> modules, MavenModule root) {
    if (modules.contains(root)) return '' //skip, root will always trigger submodules

    // sorting is to help test/debug only, there is no direct gain doing this
    return modules.sort { MavenModule m -> m.depth }
      .collect { MavenModule m -> root.pom.parentFile.toPath().relativize(m.pom.parentFile.toPath()).toString() }
      .join(',')
  }

  /**
   * if previous success is null, then this is the first build or there are no previous successful builds, return null to trigger a new build
   * if changes is an empty list, that means nothing changed, return empty list to skip this build
   *
   * @return null if no successful builds, list otherwise
   */
  @NonCPS
  List<String> getChangeSet() {
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

  static class CommandBuilder {
    List<String> goals = []
    List<String> options = []

    @NonCPS
    static CommandLine parse(String... args) {
      CLIManager cliManager = new CLIManager()
      return cliManager.parse(args)
    }

    def leftShift(String cmd) {
      plus(cmd)
    }

    def plus(String cmd) {
      if (cmd) {
        CommandLine commandLine = parse(cmd.split())
        this.goals.addAll(commandLine.args)
        this.options.addAll(commandLine.options.collect { printOpt(it) } as String[])
      }
      return this
    }

    def minus(String cmd) {
      if (cmd) {
        CommandLine commandLine = parse(cmd.split())
        this.goals.removeAll(commandLine.args)
        this.options.removeAll(commandLine.options.collect { printOpt(it) } as String[])
      }
      return this
    }

    static String printOpt(Option opt) {
      def sb = '-' << opt.getOpt()
      if (opt.hasArg()) {
        sb << (opt.getOpt() == CLIManager.SET_SYSTEM_PROPERTY ? '' : ' ') << opt.value
      }
      return sb
    }

    @NonCPS
    boolean removeOption(String opt) {
      options.removeAll { it.startsWith(opt) }
    }

    @NonCPS
    Properties getUserProperties() {
      Properties props = new Properties()
      getOptionsValues(CLIManager.SET_SYSTEM_PROPERTY).each { String property ->
        String name, value
        int i = property.indexOf('=')
        if (i <= 0) {
          name = property.trim()
          value = "true"
        } else {
          name = property.substring(0, i).trim()
          value = property.substring(i + 1).trim()
        }
        props.setProperty(name, value)
      }
      return props
    }

    @NonCPS
    List<String> getProjectList() {
      getOptionsValues(CLIManager.PROJECT_LIST)
        .collectMany { it.split(',').toList() }
    }

    @NonCPS
    List<String> getActiveProfileIds() {
      getOptionsValues(CLIManager.ACTIVATE_PROFILES)
        .findAll { it.indexOf('!') < 0 }
        .collectMany { it.split(',').toList() }
    }

    @NonCPS
    List<String> getOptionsValues(opt) {
      CommandLine commandLine = parse(options.join(' ').split())
      return commandLine.getOptionValues(opt)?.toList() ?: []
    }

    void validate() {
      goals.unique(true)
      options.unique(true)

      if (goals.empty) {
        throw new Exception("No goals have been specified for this build")
      }
      if (options.count { it.startsWith("-${CLIManager.ALTERNATE_POM_FILE} ") } > 1) {
        throw new Exception("Only one alternate POM file is allowed")
      }
    }

    String build() {
      validate()
      StringBuilder sb = new StringBuilder()
      sb << BASE_COMMAND << ' '
      sb << goals.join(' ') << ' '
      sb << options.join(' ')
      return sb
    }
  }
}
