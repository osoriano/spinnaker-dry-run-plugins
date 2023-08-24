package com.osoriano.spinnaker.plugin.postdeploy.dryrun

import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.actuation.SubjectType
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.plugins.PostDeployActionHandler
import com.netflix.spinnaker.keel.api.postdeploy.PostDeployAction
import com.netflix.spinnaker.keel.api.postdeploy.SupportedPostDeployActionType
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.osoriano.spinnaker.plugin.task.DryRunTasks
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** A dry run post deploy action handler with configurable behavior */
@Component
class DryRunPostDeployActionHandler(
  override val eventPublisher: EventPublisher,
  private val taskLauncher: TaskLauncher,
  private val dryRunTasks: DryRunTasks,
) : PostDeployActionHandler<DryRunPostDeployAction> {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override val supportedType = SupportedPostDeployActionType<DryRunPostDeployAction>(DRY_RUN_POST_DEPLOY_V1)

  /**
   * Start the post deploy action for this artifact and environment.
   * The returned Map is persisted and is passed to evaluate
   */
  override suspend fun start(
    context: ArtifactInEnvironmentContext,
    action: PostDeployAction,
  ): Map<String, Any?> {
    require(action is DryRunPostDeployAction) {
      val message = (
        "Invalid post deploy action type: ${action.javaClass.canonicalName}. " +
          "Expected DryRunPostDeployAction"
        )
      log.error(message)
      message
    }

    val application = context.deliveryConfig.application
    log.info(
      "Post deploy action starting for {} {}",
      application,
      context.environmentName,
    )

    val stages = dryRunTasks.createDryRunTask(
      "dryrun post deploy action wait",
      action.waitTime,
      action.fail,
    )

    if (stages.isEmpty()) {
      return emptyMap()
    }

    // Launch task.
    val task = taskLauncher.submitJob(
      user = "Spinnaker",
      application = application,
      notifications = context.environment.notifications,
      environmentName = context.environmentName,
      // Only resource tasks are monitored separately. Safe to use null here
      // See OrcaTaskMonitorAgent
      resourceId = null,
      description = "dryrun post deploy action (${context.version})",
      // Upstream code proposes not passing a correlation id
      // See https://github.com/spinnaker/keel/pull/1817
      correlationId = null,
      stages = stages,
      // is not supported in keel
      // type = SubjectType.POST_DEPLOY,
      type = SubjectType.VERIFICATION,
    )

    val link = "/#/applications/$application/tasks/${task.id}"
    return mapOf(
      "id" to task.id,
      "name" to task.name,
      "link" to link,
    )
  }

  override suspend fun evaluate(
    context: ArtifactInEnvironmentContext,
    action: PostDeployAction,
    oldState: ActionState,
  ): ActionState {
    require(action is DryRunPostDeployAction) {
      val message = (
        "Invalid post deploy cation type: ${action.javaClass.canonicalName}. " +
          "Expected DryRunPostDeployAction"
        )
      log.error(message)
      message
    }

    val application = context.deliveryConfig.application
    val metadata = oldState.metadata
    log.info(
      "Evaluating post deploy action for {} {}: {}",
      application,
      context.environmentName,
      metadata,
    )

    if ("id" !in metadata) {
      // No task to complete. Mark as passed.
      return oldState.copy(status = ConstraintStatus.PASS)
    }

    val taskId = metadata["id"] as String
    val link = metadata["link"] as String

    val taskExecution = taskLauncher.getTaskExecution(taskId)

    if (taskExecution.status.isFailure()) {
      log.info(
        "Post deploy action status for {} {} is {} ",
        context.deliveryConfig.application,
        context.environmentName,
        ConstraintStatus.FAIL,
      )
      return oldState.copy(status = ConstraintStatus.FAIL, link = link)
    }

    if (taskExecution.status.isSuccess()) {
      log.info(
        "Post deploy action status for {} {} is {}",
        context.deliveryConfig.application,
        context.environmentName,
        ConstraintStatus.PASS,
      )
      return oldState.copy(status = ConstraintStatus.PASS, link = link)
    }

    return oldState.copy(status = ConstraintStatus.PENDING, link = link)
  }
}
