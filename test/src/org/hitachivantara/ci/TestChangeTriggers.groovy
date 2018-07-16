package org.hitachivantara.ci

import hudson.model.Run
import org.hitachivantara.ci.build.BuilderFactory
import org.hitachivantara.ci.build.IBuilder
import org.hitachivantara.ci.config.BuildData
import org.hitachivantara.ci.config.BuildDataBuilder
import org.hitachivantara.ci.utils.ClosureInspectionHelper
import org.hitachivantara.ci.utils.MockDslScript
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper
import spock.lang.Shared
import spock.lang.Specification

class TestChangeTriggers extends Specification {
  @Shared Script script
  @Shared Map builderData

  def setupSpec() {
    script = new MockDslScript()
    BuildData buildData = new BuildDataBuilder(script)
        .withDefaults('test/resources/buildDefaults.yaml')
        .withBuildData('test/resources/buildDataSample.yaml')
        .build()
    builderData = [
        buildData: buildData,
        dsl      : script
    ]

    def wrapper = GroovyMock(RunWrapper)
    wrapper.getRawBuild() >> GroovyMock(Run)
    script.binding.setVariable('currentBuild', wrapper)
  }

  def "test upstream change naively forces all downstream builds"() {
    setup:
      LinkedHashMap<String, List<JobItem>> buildMap = builderData.buildData.buildMap
      buildMap.values().flatten().each { it.setExecType(JobItem.ExecutionType.AUTO) }
    and: "has more than 1 group"
      assert buildMap.keySet().size() > 1
    and: "all job items execution type are set to AUTO"
      assert buildMap.values().flatten().every { it.execAuto }

    when: "first job gets executed"
      JobItem firstJob = buildMap['20'].first()
      IBuilder firstJobBuilder = BuilderFactory.getBuildManager(firstJob, builderData)
      Closure build = firstJobBuilder.getBuildClosure(firstJob)
      ClosureInspectionHelper helper = new ClosureInspectionHelper(delegate: firstJobBuilder)
      build.resolveStrategy = Closure.DELEGATE_ONLY
      build.delegate = helper
      build.call()

    then: "the remaining other group jobs are set to FORCE"
      buildMap['30'].every { it.execForce }
      buildMap['30'].every { it.execForce }
  }
}
