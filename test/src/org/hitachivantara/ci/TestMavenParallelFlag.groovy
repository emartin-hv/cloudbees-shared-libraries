package org.hitachivantara.ci

import org.hitachivantara.ci.build.BuilderFactory
import org.hitachivantara.ci.build.impl.MavenBuilder
import org.hitachivantara.ci.config.BuildData
import org.hitachivantara.ci.config.BuildDataBuilder
import org.hitachivantara.ci.utils.ClosureInspectionHelper
import org.hitachivantara.ci.utils.MockDslScript
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static org.hitachivantara.ci.build.helper.BuilderUtils.ADDITIVE_EXPR

class TestMavenParallelFlag extends Specification {
  @Shared
  Script script = new MockDslScript()
  @Shared
  Map builderData

  def setupSpec() {
    BuildData buildData = new BuildDataBuilder(script)
        .withDefaults('test/resources/buildDefaults.yaml')
        .withBuildData('test/resources/thinBuildDataTestFile.yaml')
        .build()
    builderData = [
        buildData: buildData,
        dsl      : script
    ]
  }

  @Unroll
  def "job data is expanded for parallel entries with overrides: '#overrides'"() {
    setup:
      JobItem jobItem = new JobItem('',
          ['buildFramework': 'Maven', 'parallelize': 'true'] + overrides,
          ['BUILDS_ROOT_PATH': 'test/resources/multi-module-profiled-project'])
      MavenBuilder builder = BuilderFactory.getBuildManager(jobItem, builderData) as MavenBuilder
    when:
      List items = builder.expandWorkItem(jobItem)
    then:
      items.size() == expected.size()
      items.eachWithIndex { List<JobItem> workItems, int i ->
        workItems.eachWithIndex { JobItem workItem, int j ->
          assertBuildDirectives(workItem, expected[i][j])
        }
      }
    where:
      overrides << [
          ['directives': ''],
          ['directives': "${ADDITIVE_EXPR} -DskipDefault -P profile-A"],
          ['directives': "${ADDITIVE_EXPR} -pl sub-1,sub-3"],
          ['directives': 'install', 'buildFile': 'sub-1/pom.xml'],
          ['directives': 'install', 'root': 'sub-2'],
          ['directives': 'install', 'buildFile': 'sub-2/pom.xml'],
      ]

      expected << [
          [[[cmds: ['mvn clean install -Daether.connector.resumeDownloads=false -DskipTests -pl sub-1'],
            workdir: ['test/resources/multi-module-profiled-project'],
            affectedPath: 'test/resources/multi-module-profiled-project/sub-1'],
           [cmds: ['mvn clean install -Daether.connector.resumeDownloads=false -DskipTests -pl sub-2,sub-2/sub-1,sub-2/subsub-2'],
            workdir: ['test/resources/multi-module-profiled-project'],
            affectedPath: 'test/resources/multi-module-profiled-project/sub-2'],
           [cmds: ['mvn clean install -Daether.connector.resumeDownloads=false -DskipTests -pl sub-3,sub-3/sub-1'],
            workdir: ['test/resources/multi-module-profiled-project'],
            affectedPath: 'test/resources/multi-module-profiled-project/sub-3']
          ]],

          [[[cmds: ['mvn clean install -Daether.connector.resumeDownloads=false -DskipTests -DskipDefault -P profile-A -pl sub-1'],
            workdir: ['test/resources/multi-module-profiled-project'],
            affectedPath: 'test/resources/multi-module-profiled-project/sub-1'],
           [cmds: ['mvn clean install -Daether.connector.resumeDownloads=false -DskipTests -DskipDefault -P profile-A -pl sub-2,sub-2/sub-1,sub-2/subsub-2'],
            workdir: ['test/resources/multi-module-profiled-project'],
            affectedPath: 'test/resources/multi-module-profiled-project/sub-2']
          ]],

          [[[cmds: ['mvn clean install -Daether.connector.resumeDownloads=false -DskipTests -pl sub-1'],
            workdir: ['test/resources/multi-module-profiled-project'],
            affectedPath: 'test/resources/multi-module-profiled-project/sub-1'],
           [cmds: ['mvn clean install -Daether.connector.resumeDownloads=false -DskipTests -pl sub-3,sub-3/sub-1'],
            workdir: ['test/resources/multi-module-profiled-project'],
            affectedPath: 'test/resources/multi-module-profiled-project/sub-3']
          ]],

          [[[cmds: ['mvn install -f sub-1/pom.xml -Daether.connector.resumeDownloads=false -DskipTests'],
            workdir: ['test/resources/multi-module-profiled-project'],
            affectedPath: 'test/resources/multi-module-profiled-project']
          ]],

          [[[cmds: ['mvn install -Daether.connector.resumeDownloads=false -DskipTests -pl sub-1'],
            workdir: ['test/resources/multi-module-profiled-project/sub-2'],
            affectedPath: 'test/resources/multi-module-profiled-project/sub-2/sub-1'],
           [cmds: ['mvn install -Daether.connector.resumeDownloads=false -DskipTests -pl subsub-2'],
            workdir: ['test/resources/multi-module-profiled-project/sub-2'],
            affectedPath: 'test/resources/multi-module-profiled-project/sub-2/subsub-2']
          ]],

          [[[cmds: ['mvn install -f sub-2/pom.xml -Daether.connector.resumeDownloads=false -DskipTests -pl sub-1'],
            workdir: ['test/resources/multi-module-profiled-project'],
            affectedPath: 'test/resources/multi-module-profiled-project/sub-2/sub-1'],
           [cmds: ['mvn install -f sub-2/pom.xml -Daether.connector.resumeDownloads=false -DskipTests -pl subsub-2'],
            workdir: ['test/resources/multi-module-profiled-project'],
            affectedPath: 'test/resources/multi-module-profiled-project/sub-2/subsub-2']
          ]]
      ]
  }

  def "test inter-dependencies project"() {
    setup:
      JobItem jobItem = new JobItem('',
          ['buildFramework': 'Maven', 'parallelize': 'true'] + overrides,
          ['BUILDS_ROOT_PATH': 'test/resources/inter-module-dependency-project'])
      MavenBuilder builder = BuilderFactory.getBuildManager(jobItem, builderData) as MavenBuilder
    when:
      List items = builder.expandWorkItem(jobItem)
    then:
      items.size() == expected.size()
      items.eachWithIndex { List<JobItem> workItems, int i ->
        workItems.eachWithIndex { JobItem workItem, int j ->
          assertBuildDirectives(workItem, expected[i][j])
        }
      }
    where:
      overrides << [
          ['directives': ''],
          ['directives': 'install -pl sub-3,sub-2,sub-1'],
      ]

      expected << [
          [[// parallel group
              // parallel subgroup 1, all projects without parent dependencies
              [cmds: ['mvn clean install -Daether.connector.resumeDownloads=false -DskipTests -pl sub-1']],
              [cmds: ['mvn clean install -Daether.connector.resumeDownloads=false -DskipTests -pl sub-4']],
           ],
           [
               // parallel group 2, all projects that depends on previous subgroups
              [cmds: ['mvn clean install -Daether.connector.resumeDownloads=false -DskipTests -pl sub-2']],
           ],
           [
               // parallel group 3, all projects that depends on previous subgroups
              [cmds: ['mvn clean install -Daether.connector.resumeDownloads=false -DskipTests -pl sub-5']],
              [cmds: ['mvn clean install -Daether.connector.resumeDownloads=false -DskipTests -pl sub-3']],
           ]],

          [[
               // parallel subgroup 1, all projects without parent dependencies
              [cmds: ['mvn install -Daether.connector.resumeDownloads=false -DskipTests -pl sub-1']],
           ],
           [
               // parallel group 2, all projects that depends on previous subgroups
              [cmds: ['mvn install -Daether.connector.resumeDownloads=false -DskipTests -pl sub-2']],
           ],
           [
               // parallel group 3, all projects that depends on previous subgroups
              [cmds: ['mvn install -Daether.connector.resumeDownloads=false -DskipTests -pl sub-3']],
           ]],
      ]
  }

  private assertBuildDirectives(JobItem jobItem, Map<String, Object> expected) {
    MavenBuilder builder = BuilderFactory.getBuildManager(jobItem, builderData) as MavenBuilder
    ClosureInspectionHelper buildHelper = new ClosureInspectionHelper(delegate: script)

    Closure mvnBuild = builder.getBuildClosure(jobItem)
    mvnBuild.resolveStrategy = Closure.DELEGATE_ONLY
    mvnBuild.delegate = buildHelper
    mvnBuild()

    expected.each { String k, v ->
      if (buildHelper.hasProperty(k)) {
        assert buildHelper[k] == v
      }
      if (jobItem.hasProperty(k)) {
        assert jobItem[k] == v
      }
    }
  }
}
