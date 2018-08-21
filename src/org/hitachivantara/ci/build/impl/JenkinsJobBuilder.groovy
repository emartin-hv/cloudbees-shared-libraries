package org.hitachivantara.ci.build.impl

import org.hitachivantara.ci.JobItem
import org.hitachivantara.ci.build.IBuilder
import org.hitachivantara.ci.config.BuildData
import org.hitachivantara.ci.config.FilteredMapWithDefault

/**
 * This isn't really a builder but it is the best way to introduce
 * this new feature into the building system
 */
class JenkinsJobBuilder implements IBuilder {

  private BuildData buildData
  private Script dsl

  void setBuilderData(Map builderData) {
    this.buildData = builderData['buildData']
    this.dsl = builderData['dsl']
  }

  Closure getBuildClosure(JobItem jobItem) {

    if (buildData.noop || jobItem.execNoop) {
      return { -> dsl.log.info "${jobItem.getJobID()} NOOP so not calling ${jobItem.targetJobName}" }
    }

    List parameters = []

    if (jobItem.passOnBuildParameters()) {
      parameters.addAll(
        collectParameters(
          buildData.buildProperties.findAll { key, value ->
            (key != 'JOB_ITEM_DEFAULTS' && key != 'properties')
          }
        )
      )
    }

    if(jobItem.jobProperties) {
      Map filteredProperties = new FilteredMapWithDefault(buildData.buildProperties)
      filteredProperties << jobItem.jobProperties
      parameters.addAll(collectParameters(filteredProperties))
    }

    dsl.log.info "Properties to pass on: [${parameters}]"

    return {
      dsl.build(
        job: jobItem.targetJobName,
        wait: !jobItem.isAsynchronous(),
        parameters: parameters
      )
    }
  }

  List collectParameters(Map properties) {
    List parameters = []

    properties.keySet().each { key ->
      // use get() to force filtering
      def value = properties.get(key)

      if (value instanceof Boolean) {
        parameters << dsl.booleanParam(name: key, value: value)
      } else {
        parameters << dsl.string(name: key, value: String.valueOf(value))
      }
    }

    return parameters
  }

  Closure getTestClosure(JobItem jobItem) {
    return {}
  }

  List<List<JobItem>> expandWorkItem(JobItem jobItem) {
    dsl.log.warn "Expanding jobItem not implemented for JenkinsJob, reverting to normal"
    [[jobItem]]
  }
}
