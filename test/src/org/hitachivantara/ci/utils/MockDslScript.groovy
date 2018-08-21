package org.hitachivantara.ci.utils

/**
 * Use this class in place of the pipeline script to recreate any behaviour for tests
 */
class MockDslScript extends Script {

  def propertyMissing(String name) {
    this
  }

  def methodMissing(String methodName, args) {
    // do nothing
  }

  Object run() {
    // do nothing
  }

  String booleanParam(Map values) {
    return values.toString()
  }

  String string(Map values) {
    return values.toString()
  }
}
