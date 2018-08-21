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

import hudson.model.Result
import jenkins.model.CauseOfInterruption
import org.hitachivantara.ci.build.helper.BuilderUtils
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

/**
 * Call the body, and catch any exception.
 * If an exception occurs, it calls the handler for custom error manipulation. However the handler is ignored if the
 * body was terminated, and the error is rethrown.
 *
 * The default implementation is equivalent to:
 * <pre> {@code
 * try {
 *   body()
 * } catch (Throwable e) {
 *   handler(e)
 * } finally {
 *   last()
 * }
 * }</pre>
 * @param body
 * @param handler
 * @param last
 * @return
 */
def handleError(Closure body, Closure handler = {}, Closure last = {}) {
  try {
    body.call()
  }
  catch (FlowInterruptedException fie) {
    //bypass handler
    throw fie
  }
  catch (Throwable err) {
    int exitCode = BuilderUtils.getExitCode(err.message)
    int signalCode = BuilderUtils.getErrorSignal(exitCode)
    if (signalCode == 15) {
      // this job was forcefully terminated, bypass handler
      throw new FlowInterruptedException(Result.NOT_BUILT, new CauseOfInterruption() {
        @Override
        String getShortDescription() {
          return 'Job item was terminated: SIGTERM'
        }
      })
    }
    handler.call(err)
  }
  finally {
    last.call()
  }
}