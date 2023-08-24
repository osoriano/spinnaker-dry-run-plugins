package com.osoriano.spinnaker.plugin.verification.base

import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.actuation.SubjectType
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.plugins.VerificationEvaluator
import com.netflix.spinnaker.keel.model.OrcaJob
import com.osoriano.spinnaker.plugin.rollback.RollbackHandler
import com.osoriano.spinnaker.plugin.verification.config.VerificationConfig
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Verification evaluator that manages Orca tasks.
 * Also supports auto rollbacks and retries.
 */
abstract class OrcaTaskVerificationEvaluator<V : AutoRollbackVerification>(
  verificationType: String,
  verificationClass: Class<V>,
  private val taskLauncher: TaskLauncher,
  private val rollbackHandler: RollbackHandler,
  private val verificationConfig: VerificationConfig,
) : BaseVerificationEvaluator<V>(
  verificationType,
  verificationClass,
),
  VerificationEvaluator<V> {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  /**
   * Start the verification for this artifact and environment.
   * The returned Map is persisted and is passed to evaluate
   */
  override fun startVerification(
    context: ArtifactInEnvironmentContext,
    verification: V,
  ): Map<String, Any?> {
    return startWithRetries(context, verification, verificationConfig.maxRetries)
  }

  private fun startWithRetries(
    context: ArtifactInEnvironmentContext,
    verification: V,
    remainingRetries: Int,
  ): Map<String, Any?> {
    val application = context.deliveryConfig.application
    log.info(
      "Verification starting for {} {}",
      application,
      context.environmentName,
    )

    val stages = getOrcaVerificationStages(context, verification)
    val description = getOrcaTaskDescription(context, verification)

    // Launch task.
    val task = runBlocking {
      taskLauncher.submitJob(
        user = "Spinnaker",
        application = application,
        notifications = context.environment.notifications,
        environmentName = context.environmentName,
        // Only resource tasks are monitored separately. Safe to use null here
        // See OrcaTaskMonitorAgent
        resourceId = null,
        description = description,
        // Upstream code proposes not passing a correlation id
        // See https://github.com/spinnaker/keel/pull/1817
        correlationId = null,
        stages = stages,
        type = SubjectType.VERIFICATION,
      )
    }

    val link = "/#/applications/$application/tasks/${task.id}"
    return mapOf(
      "id" to task.id,
      "name" to task.name,
      "link" to link,
      "remainingRetries" to remainingRetries,
    )
  }

  abstract fun getOrcaVerificationStages(
    context: ArtifactInEnvironmentContext,
    verification: V,
  ): List<OrcaJob>

  abstract fun getOrcaTaskDescription(
    context: ArtifactInEnvironmentContext,
    verification: V,
  ): String

  override fun evaluateVerification(
    context: ArtifactInEnvironmentContext,
    verification: V,
    oldState: ActionState,
  ): ActionState {
    val application = context.deliveryConfig.application
    val metadata = oldState.metadata
    log.info(
      "Evaluating verification for {} {}: {}",
      application,
      context.environmentName,
      metadata,
    )

    val taskId = metadata["id"] as String
    val link = metadata["link"] as String
    val remainingRetries = metadata["remainingRetries"] as Int

    val taskExecution = runBlocking {
      taskLauncher.getTaskExecution(taskId)
    }

    if (taskExecution.status.isFailure()) {
      if (remainingRetries > 0) {
        log.info(
          "Verification status for {} {} is {}",
          context.deliveryConfig.application,
          context.environmentName,
          ConstraintStatus.PENDING,
        )
        val updatedMetadata = startWithRetries(context, verification, remainingRetries - 1)
        val updatedLink = updatedMetadata["link"] as String
        return oldState.copy(status = ConstraintStatus.PENDING, link = updatedLink, metadata = updatedMetadata)
      }
      rollbackHandler.handleFailure(
        context,
        verification.rollbackBehavior,
      )

      log.info(
        "Verification status for {} {} is {} " +
          "with rollbackBehavior {}",
        context.deliveryConfig.application,
        context.environmentName,
        ConstraintStatus.FAIL,
        verification.rollbackBehavior,
      )
      return oldState.copy(status = ConstraintStatus.FAIL, link = link)
    }
    if (taskExecution.status.isSuccess()) {
      log.info(
        "Verification status for {} {} is {}",
        context.deliveryConfig.application,
        context.environmentName,
        ConstraintStatus.PASS,
      )
      return oldState.copy(status = ConstraintStatus.PASS, link = link)
    }

    return oldState.copy(status = ConstraintStatus.PENDING, link = link)
  }
}
