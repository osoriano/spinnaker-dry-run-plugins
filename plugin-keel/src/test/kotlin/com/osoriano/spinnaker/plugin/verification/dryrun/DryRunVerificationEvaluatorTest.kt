package com.osoriano.spinnaker.plugin.verification.dryrun

import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.TaskStatus
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.actuation.SubjectType
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.model.OrcaJob
import com.netflix.spinnaker.keel.orca.ExecutionDetailResponse
import com.osoriano.spinnaker.plugin.artifact.dryrun.DryRunArtifact
import com.osoriano.spinnaker.plugin.rollback.RollbackBehavior
import com.osoriano.spinnaker.plugin.rollback.RollbackHandler
import com.osoriano.spinnaker.plugin.task.DryRunTasks
import com.osoriano.spinnaker.plugin.verification.config.VerificationConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import java.time.Duration
import java.time.Instant

class DryRunVerificationEvaluatorTest {

  @Test
  fun testVerificationEvaluatorStart() {
    // Set up DryRunVerificationEvaluator
    val taskLauncher = mockk<TaskLauncher>()
    val rollbackHandler = mockk<RollbackHandler>()
    val verificationConfig = VerificationConfig(maxRetries = 3)
    val dryRunTasks = DryRunTasks()
    val verificationEvaluator = DryRunVerificationEvaluator(
      taskLauncher,
      rollbackHandler,
      verificationConfig,
      dryRunTasks,
    )

    // Given verification, environment, artifact
    val waitTime = Duration.ofSeconds(30)
    val verification = DryRunVerification(
      waitTime = waitTime,
      fail = true,
      RollbackBehavior.LAST_SUCCESSFUL,
    )
    val environment = Environment(
      "testEnvironment",
      verifyWith = listOf(verification),
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
        description = "verify (testEnvironment) (testVersion)",
        correlationId = null,
        stages = listOf(
          OrcaJob(
            "wait",
            mapOf(
              "name" to "dryrun verification wait",
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

    // Start the verification
    val startResult = verificationEvaluator.start(context, verification)
    assertEquals(startResult["id"], "testTaskId")
    assertEquals(startResult["name"], "testTaskName")
    assertEquals(startResult["link"], "/#/applications/testApplication/tasks/testTaskId")
    assertEquals(startResult["remainingRetries"], 3)
  }

  @Test
  fun testVerificationEvaluatorEvaluateFailWithRetry() {
    // Set up DryRunVerificationEvaluator
    val taskLauncher = mockk<TaskLauncher>()
    val rollbackHandler = mockk<RollbackHandler>()
    val verificationConfig = VerificationConfig(maxRetries = 1)
    val dryRunTasks = DryRunTasks()
    val verificationEvaluator = DryRunVerificationEvaluator(
      taskLauncher,
      rollbackHandler,
      verificationConfig,
      dryRunTasks,
    )

    // Given verification, environment, artifact
    val waitTime = Duration.ofSeconds(30)
    val verification = DryRunVerification(
      waitTime = waitTime,
      fail = true,
      RollbackBehavior.LAST_SUCCESSFUL,
    )
    val environment = Environment(
      "testEnvironment",
      verifyWith = listOf(verification),
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
        "id" to "testExecutionId1",
        "name" to "testExecutionName1",
        "link" to "testExecutionLink1",
        "remainingRetries" to 1,
      ),
    )

    // Mock the task failures
    for (i in 1..2) {
      coEvery {
        taskLauncher.getTaskExecution("testExecutionId$i")
      } returns ExecutionDetailResponse(
        "testExecutionId$i",
        "testExecutionName$i",
        "testApplication",
        buildTime = startedAtTime,
        startTime = startedAtTime,
        endTime = null,
        status = TaskStatus.TERMINAL,
      )
    }

    // Mock the task retry
    coEvery {
      taskLauncher.submitJob(
        user = "Spinnaker",
        application = "testApplication",
        notifications = emptySet(),
        environmentName = "testEnvironment",
        resourceId = null,
        description = "verify (testEnvironment) (testVersion)",
        correlationId = null,
        stages = listOf(
          OrcaJob(
            "wait",
            mapOf(
              "name" to "dryrun verification wait",
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
      "testExecutionId2",
      "testExecutionName2",
    )

    // Mock the rollback handling
    every {
      rollbackHandler.handleFailure(
        context = context,
        RollbackBehavior.LAST_SUCCESSFUL,
      )
    } returns Unit

    // Evaluate the verification
    // First attempt results in a retry
    val retryState = verificationEvaluator.evaluate(context, verification, oldState)
    assertEquals(retryState.status, ConstraintStatus.PENDING)
    assertEquals(retryState.link, "/#/applications/testApplication/tasks/testExecutionId2")

    // Second attempt fails
    val newState = verificationEvaluator.evaluate(context, verification, retryState)
    assertEquals(newState.status, ConstraintStatus.FAIL)
    assertEquals(newState.link, "/#/applications/testApplication/tasks/testExecutionId2")
  }

  class InvalidVerification : Verification {
    override val type = "unknown/verification-plugin@v1"
    override val id = "unknown/verification-plugin@v1"
  }

  @Test
  fun testInvalidVerification() {
    // Set up DryRunVerificationEvaluator
    val taskLauncher = mockk<TaskLauncher>()
    val rollbackHandler = mockk<RollbackHandler>()
    val verificationConfig = VerificationConfig(maxRetries = 3)
    val dryRunTasks = DryRunTasks()
    val verificationEvaluator = DryRunVerificationEvaluator(
      taskLauncher,
      rollbackHandler,
      verificationConfig,
      dryRunTasks,
    )

    // Given verification, environment, artifact
    val verification = InvalidVerification()
    val environment = Environment(
      "testEnvironment",
      verifyWith = listOf(verification),
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

    // Start the verification with the wrong verification type
    val actualVerification = "com.osoriano.spinnaker.plugin.verification.dryrun.DryRunVerificationEvaluatorTest.InvalidVerification"
    val expectedVerification = "com.osoriano.spinnaker.plugin.verification.dryrun.DryRunVerification"
    val illegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
      verificationEvaluator.start(context, verification)
    }
    assertEquals(
      illegalArgumentException.message,
      "Invalid verification type: $actualVerification. Expected $expectedVerification",
    )
  }
}
