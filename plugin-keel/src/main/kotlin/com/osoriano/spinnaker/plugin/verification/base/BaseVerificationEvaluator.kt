package com.osoriano.spinnaker.plugin.verification.base

import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.plugins.VerificationEvaluator
import org.slf4j.LoggerFactory

/**
 * Base verification evaluator that validates the verification type
 */
abstract class BaseVerificationEvaluator<V : Verification>(
  val verificationType: String,
  val verificationClass: Class<V>,
) : VerificationEvaluator<V> {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override val supportedVerification = verificationType to verificationClass

  /**
   * Start the verification for this artifact and environment.
   * The returned Map is persisted and is passed to evaluate
   */
  override fun start(
    context: ArtifactInEnvironmentContext,
    verification: Verification,
  ): Map<String, Any?> {
    validateVerificationClass(verification)
    return startVerification(context, verificationClass.cast(verification))
  }

  /**
   * Same as start, but uses the concrete verification type
   */
  abstract fun startVerification(
    context: ArtifactInEnvironmentContext,
    verification: V,
  ): Map<String, Any?>

  /**
   * Evaluate verification for this artifact and environment
   * given the action state
   */
  override fun evaluate(
    context: ArtifactInEnvironmentContext,
    verification: Verification,
    oldState: ActionState,
  ): ActionState {
    validateVerificationClass(verification)
    return evaluateVerification(
      context,
      verificationClass.cast(verification),
      oldState,
    )
  }

  /**
   * Same as evaluate, but uses the concrete verification type
   */
  abstract fun evaluateVerification(
    context: ArtifactInEnvironmentContext,
    verification: V,
    oldState: ActionState,
  ): ActionState

  /**
   * Validate that the verification is of the expected type
   * and cast it
   */
  private fun validateVerificationClass(verification: Verification) {
    require(verificationClass.isInstance(verification)) {
      val message = (
        "Invalid verification type: ${verification.javaClass.canonicalName}. " +
          "Expected ${verificationClass.canonicalName}"
        )
      log.error(message)
      message
    }
  }
}
