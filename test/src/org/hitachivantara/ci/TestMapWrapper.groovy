package org.hitachivantara.ci

import org.hitachivantara.ci.config.FilteredMapWithDefault
import spock.lang.Specification

class TestMapWrapper extends Specification {

  def "test getInt return's an Integer"() {
    given:
      FilteredMapWithDefault map = new FilteredMapWithDefault([:])
      map.putAll(defaults)

    expect: "asking for the specific type should return anything but null"
      map.getInt('k').class == Integer

    where:
      defaults << [
          [:],
          ['k': 1],
          ['k': null]
      ]
  }

  def "test getDouble return's a Double"() {
    given:
      FilteredMapWithDefault map = new FilteredMapWithDefault([:])
      map.putAll(defaults)

    expect: "asking for the specific type should return anything but null"
      map.getDouble('k').class == Double

    where:
      defaults << [
          [:],
          ['k': 1d],
          ['k': null]
      ]
  }

  def "test getBool return's a Boolean"() {
    given:
      FilteredMapWithDefault map = new FilteredMapWithDefault([:])
      map.putAll(defaults)

    expect: "asking for the specific type should return anything but null"
      map.getBool('k').class == Boolean

    where:
      defaults << [
          [:],
          ['k': true],
          ['k': null]
      ]
  }

  def "test getString return's a String"() {
    given:
      FilteredMapWithDefault map = new FilteredMapWithDefault([:])
      map.putAll(defaults)

    expect: "asking for the specific type should return anything but null"
      map.getString('k').class == String

    where:
      defaults << [
          [:],
          ['k': 'hello world'],
          ['k': null]
      ]
  }
}
