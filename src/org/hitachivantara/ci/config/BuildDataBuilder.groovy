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

package org.hitachivantara.ci.config

import org.hitachivantara.ci.JobItem
import org.yaml.snakeyaml.Yaml

class BuildDataBuilder {

  private Script dsl

  private def env
  private Map params = [:]
  private String buildDataPath
  private String defaultConfigPath

  BuildDataBuilder(Script caller) {
    this.dsl = caller
  }

  /**
   * Pipeline execution parameters
   *
   * @param params
   * @return
   */
  BuildDataBuilder withParams(Map params) {
    this.params = params
    return this
  }

  /**
   * Jenkins environment parameters
   *
   * @param env
   * @return
   */
  BuildDataBuilder withEnvironment(env) {
    // Jenkins will send us an instance of org.jenkinsci.plugins.workflow.cps.EnvActionImpl
    // including the project dependency to strongly type it here will cause some issues with
    // test compilation. Let it be generic and make it easier on tests by allowing a map too.
    this.env = env
    return this
  }

  /**
   * Default build properties
   *
   * @param defaultConfigPath
   * @return
   */
  BuildDataBuilder withDefaults(String defaultConfigPath) {
    this.defaultConfigPath = defaultConfigPath
    return this
  }

  /**
   * Pipeline build file
   *
   * @param buildDataPath
   * @return
   */
  BuildDataBuilder withBuildData(String buildDataPath) {
    this.buildDataPath = buildDataPath
    return this
  }

  /**
   * Creates and returns the configuration to be used on the build
   *
   * @return
   */
  BuildData build() {
    // merge all the properties sources in order of override, using environment as the base
    Map mergedProperties = new FilteredMapWithDefault(env ?: [:])

    Map defaultProperties = loadDefaultProperties(mergedProperties)
    mergedProperties << defaultProperties

    // apply any param overrides given
    Map overridenParams = [:]
    overridenParams << params
    overridenParams << parseOverrides(params)

    // send the params in here too so we can get the build file from it
    // but don't merge the params yet cause we want them after the build file properties
    Map buildData = loadBuildData(mergedProperties + overridenParams)
    Map buildProperties = buildData['buildProperties'] as Map ?: [:]
    mergedProperties << buildProperties

    mergedProperties << overridenParams

    StringBuilder sb = new StringBuilder()
    sb << 'Resolved build properties:'
    mergedProperties.sort().each { key, value ->
      sb << '\n'
      sb << "${key}: ${value}"
    }
    dsl.echo(sb.toString())

    Map<String, List<JobItem>> jobBuildMap = parseJobData(buildData['jobGroups'] as Map, mergedProperties)
    sb = new StringBuilder()
    sb << 'Job execution plan:'
    jobBuildMap.each { String group, List<JobItem> jobItems ->
      sb << '\n'
      sb << "[${group}]"

      jobItems.each { JobItem jobItem ->
        sb << '\n'
        sb << jobItem.toString()
      }
    }
    dsl.echo(sb.toString())

    return new BuildData(
        buildMap: jobBuildMap,
        buildProperties: mergedProperties
    )
  }

  /**
   * Parses build file data to gather all the job and jobGroup information
   *
   * @param jobGroups
   * @param buildProperties
   * @return
   */
  private Map<String, List<JobItem>> parseJobData(Map jobGroups, Map buildProperties) {
    // read job parameter overrides
    Map jobOverrides = parseJobOverrides(buildProperties)

    // first and last job flags
    String firstJob = buildProperties.FIRST_JOB?.trim() ?: null
    String lastJob = buildProperties.LAST_JOB?.trim() ?: null

    // Setup a state variable on whether jobs should ran
    // if a first/last job was defined
    boolean executeJobs = !firstJob

    Map<String, List<JobItem>> jobBuildMap = [:]

    // parse the jobGroups in the build file into JobItems
    jobGroups.each { jobGroup, List jobs ->
      List jobItems = jobs.collect { Map job ->
        // Apply any existing property overrides
        if (jobOverrides && jobOverrides[job.jobID]) {
          job << jobOverrides[job.jobID] as Map
        }

        JobItem jobItem = new JobItem(jobGroup as String, job, buildProperties)

        // Start executing jobs if a first job was set and this is the one
        if (firstJob && jobItem.jobID == firstJob) {
          executeJobs = true
        }

        // Set the job to a NOOP if before the first job or after the last job
        if (!executeJobs) {
          jobItem.setExecType(JobItem.ExecutionType.NOOP)
        }

        // Disable executing jobs if this is the last job
        if (lastJob && jobItem.jobID == lastJob) {
          executeJobs = false
        }

        return jobItem
      }

      if (jobItems) {
        jobBuildMap[jobGroup as String] = jobItems
      }
    }

    return jobBuildMap
  }

  /**
   * Parses the OVERRIDE_JOB_PARAMS build parameter to be applied to the job configuration.
   *
   * @param buildProperties
   * @return
   */
  private Map parseJobOverrides(Map buildProperties) {
    String jobOverridesParam = buildProperties.OVERRIDE_JOB_PARAMS?.trim() ? buildProperties.OVERRIDE_JOB_PARAMS : null
    Map jobOverridesMap = [:]

    if (jobOverridesParam) {
      try {
        def jobOverridesParsed = new Yaml().load(jobOverridesParam)

        // allow a single job declaration without it being a list
        List jobOverridesList = jobOverridesParsed instanceof List ? jobOverridesParsed : [jobOverridesParsed]

        jobOverridesMap = jobOverridesList.collectEntries { Map jobOverrides ->
          String jobID = jobOverrides.remove('jobID')

          [(jobID): jobOverrides]
        }
      }
      catch (Throwable e) {
        throw new IllegalArgumentException('Wrong job override param format. Use a valid yaml.', e)
      }
    }

    return jobOverridesMap
  }

  /**
   * Parses the OVERRIDE_PARAMS build parameter to be applied to the job configuration.
   *
   * @param buildProperties
   * @return
   */
  private Map parseOverrides(Map buildProperties) {
    String overridesParam = buildProperties.OVERRIDE_PARAMS?.trim() ? buildProperties.OVERRIDE_PARAMS : null
    Map overridesMap = [:]

    if (overridesParam) {
      try {
        overridesMap = new Yaml().load(overridesParam)
      }
      catch (Throwable e) {
        throw new IllegalArgumentException('Wrong override param format. Use a valid yaml.', e)
      }
    }

    return overridesMap
  }

  /**
   * Loads the build data file
   *
   * @return
   */
  private Map loadBuildData(Map buildProperties) {
    if (!buildDataPath) {
      // not directly defined, fetch from properties
      buildDataPath = "${buildProperties['BUILD_DATA_ROOT_PATH']}/${buildProperties['BUILD_DATA_FILE']}"
    }

    dsl.echo("Loading build data from [${buildDataPath}]")

    File buildDataFile = new File(buildDataPath)
    if (buildDataFile.canRead()) {
      try {
        return new Yaml().load(buildDataFile.text) as Map
      }
      catch (Throwable e) {
        throw new IOException("Unable to parse build data file: ${buildDataPath}", e)
      }
    } else {
      dsl.echo "Unable to read build data file: ${buildDataPath}"
    }
    return [:]
  }

  /**
   * Loads the default configuration file
   *
   * @return
   */
  private Map loadDefaultProperties(Map buildProperties) {
    boolean failIfMissing = true

    if (!defaultConfigPath) {
      // try getting it from properties or set it to a default
      if(buildProperties['DEFAULT_BUILD_PROPERTIES']){
        defaultConfigPath = buildProperties['DEFAULT_BUILD_PROPERTIES']
      } else{
        defaultConfigPath = "${buildProperties.WORKSPACE}/resources/config/buildProperties.yaml"
        failIfMissing = false
      }
    }

    dsl.echo("Loading default configuration from [${defaultConfigPath}]")

    File defaultConfigFile = new File(defaultConfigPath)
    if (!defaultConfigFile.exists() || !defaultConfigFile.canRead() && failIfMissing) {
      if(failIfMissing){
        throw new IOException("Unable to read default properties file: ${defaultConfigPath}")
      }

      dsl.echo("Unable to read default properties file: ${defaultConfigPath}")
      return [:]
    }

    try {
      return new Yaml().load(defaultConfigFile.text) as Map
    }
    catch (Throwable e) {
      throw new IOException("Unable to parse default properties file: ${defaultConfigPath}", e)
    }
  }
}
