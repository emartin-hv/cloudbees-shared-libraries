package org.hitachivantara.ci.utils

/**
 * Use this class in place of the pipeline script to recreate any behaviour for tests
 */
class MockDslScript extends Script {

  def methodMissing(String methodName, args) {
    // do nothing
  }

  Object run() {
    // do nothing
  }
}
