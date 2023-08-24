package com.osoriano.spinnaker.plugin.postdeploy.dryrun

import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.TaskStatus
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.actuation.SubjectType
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.model.OrcaJob
import com.netflix.spinnaker.keel.orca.ExecutionDetailResponse
import com.osoriano.spinnaker.plugin.artifact.dryrun.DryRunArtifact
import com.osoriano.spinnaker.plugin.task.DryRunTasks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.Duration
import java.time.Instant

class DryRunPostDeployActionHandlerTest {

  @Test
  fun testPostDeployActionHandlerStart() = runBlocking {
    // Set up DryRunPostDeployActionHandler
    val eventPublisher = mockk<EventPublisher>()
    val taskLauncher = mockk<TaskLauncher>()
    val dryRunTasks = DryRunTasks()
    val postDeployActionHandler = DryRunPostDeployActionHandler(
      eventPublisher,
      taskLauncher,
      dryRunTasks,
    )

    // Given post deploy action, environment, artifact
    val postDeployAction = DryRunPostDeployAction(
      waitTime = Duration.ofSeconds(30),
      fail = true,
    )
    val environment = Environment(
      "testEnvironment",
      postDeploy = listOf(postDeployAction),
    )
    val artifact = DryRunArtifact(
      "testDeliveryArtifact",
      "testDeliveryConfig",
      "testArtifactReference",
    )
    val deliveryConfig = DeliveryConfig(
      application = "testApplication",
      name = "testDeliveryConfig",
      serviceAccount = "testServiceAccount",
      artifacts = setOf(artifact),
      environments = setOf(environment),
    )
    val context = ArtifactInEnvironmentContext(
      deliveryConfig,
      "testEnvironment",
      "testArtifactReference",
      "testVersion",
    )

    // Mock the task submission
    coEvery {
      taskLauncher.submitJob(
        user = "Spinnaker",
        application = "testApplication",
        notifications = emptySet(),
        environmentName = "testEnvironment",
        resourceId = null,
        description = "dryrun post deploy action (testVersion)",
        correlationId = null,
        stages = listOf(
          OrcaJob(
            "wait",
            mapOf(
              "name" to "dryrun post deploy action wait",
              "waitTime" to 30L,
            ),
          ),
          OrcaJob(
            "checkPreconditions",
            mapOf(
              "name" to "dryrun fail",
              "preconditions" to listOf(
                mapOf(
                  "context" to mapOf(
                    "expression" to "false",
                  ),
                  "failPipeline" to true,
                  "type" to "expression",
                ),
              ),
            ),
          ),
        ),
        type = SubjectType.VERIFICATION,
      )
    } returns Task(
      "testTaskId",
      "testTaskName",
    )

    // Start the post deploy action
    val startResult = postDeployActionHandler.start(context, postDeployAction)
    assertEquals(startResult["id"], "testTaskId")
    assertEquals(startResult["name"], "testTaskName")
    assertEquals(startResult["link"], "/#/applications/testApplication/tasks/testTaskId")
  }

  @Test
  fun testPostDeployActionHandlerEvaluate() = runBlocking {
    // Set up DryRunPostDeployActionHandler
    val eventPublisher = mockk<EventPublisher>()
    val taskLauncher = mockk<TaskLauncher>()
    val dryRunTasks = DryRunTasks()
    val postDeployActionHandler = DryRunPostDeployActionHandler(
      eventPublisher,
      taskLauncher,
      dryRunTasks,
    )

    // Given post deploy action, environment, artifact
    val postDeployAction = DryRunPostDeployAction(
      waitTime = Duration.ofSeconds(30),
      fail = true,
    )
    val environment = Environment(
      "testEnvironment",
      postDeploy = listOf(postDeployAction),
    )
    val artifact = DryRunArtifact(
      "testDeliveryArtifact",
      "testDeliveryConfig",
      "testArtifactReference",
    )
    val deliveryConfig = DeliveryConfig(
      application = "testApplication",
      name = "testDeliveryConfig",
      serviceAccount = "testServiceAccount",
      artifacts = setOf(artifact),
      environments = setOf(environment),
    )
    val context = ArtifactInEnvironmentContext(
      deliveryConfig,
      "testEnvironment",
      "testArtifactReference",
      "testVersion",
    )

    val startedAtTime = Instant.parse("2023-01-25T12:00:00.00Z")
    val oldState = ActionState(
      status = ConstraintStatus.PENDING,
      startedAt = startedAtTime,
      endedAt = null,
      metadata = mapOf<String, Any?>(
        "id" to "testExecutionId",
        "name" to "testExecutionName",
        "link" to "testExecutionLink",
      ),
    )

    // Mock the task failure
    coEvery {
      taskLauncher.getTaskExecution("testExecutionId")
    } returns ExecutionDetailResponse(
      "testExecutionId",
      "testExecutionName",
      "testApplication",
      buildTime = startedAtTime,
      startTime = startedAtTime,
      endTime = null,
      status = TaskStatus.TERMINAL,
    )

    // Evaluate the post deploy action
    val newState = postDeployActionHandler.evaluate(context, postDeployAction, oldState)
    assertEquals(newState.status, ConstraintStatus.FAIL)
    assertEquals(newState.link, "testExecutionLink")
  }
}
