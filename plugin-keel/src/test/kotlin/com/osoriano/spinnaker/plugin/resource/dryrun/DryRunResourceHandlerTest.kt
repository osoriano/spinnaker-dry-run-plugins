package com.osoriano.spinnaker.plugin.resource.dryrun

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.TaskStatus
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.events.ArtifactVersionDeployed
import com.netflix.spinnaker.keel.api.events.ArtifactVersionDeploying
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.core.ResourceCurrentlyUnresolvable
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.model.OrcaJob
import com.netflix.spinnaker.keel.orca.ExecutionDetailResponse
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoSuchEntityException
import com.osoriano.spinnaker.plugin.artifact.dryrun.DryRunArtifact
import com.osoriano.spinnaker.plugin.resource.exceptions.EnvironmentNotFoundException
import com.osoriano.spinnaker.plugin.resource.exceptions.NoVersionAvailable
import com.osoriano.spinnaker.plugin.task.DryRunTasks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.Duration
import java.time.Instant

class DryRunResourceHandlerTest {

  @Test
  fun testResourceHandler() = runBlocking {
    // Set up DryRunResourceHandler
    val eventPublisher = mockk<EventPublisher>()
    every { eventPublisher.publishEvent(any()) } returns Unit
    val keelRepository = mockk<KeelRepository>()
    val taskLauncher = mockk<TaskLauncher>()
    val orcaService = mockk<OrcaService>()
    val dryRunTasks = DryRunTasks()
    val resourceHandler = DryRunResourceHandler(
      eventPublisher,
      keelRepository,
      taskLauncher,
      orcaService,
      dryRunTasks,
    )

    // Given resource
    val resourceSpec = DryRunResourceSpec(
      "testArtifactReference",
      "testName",
      waitTime = Duration.ofMinutes(5),
      fail = true,
      metadata = mapOf("application" to "testApplication"),
    )
    val resource = Resource(
      resourceHandler.supportedKind.kind,
      mapOf(
        "id" to "testId",
        "application" to "testApplication",
      ),
      resourceSpec,
    )

    // Get current state
    val task = ExecutionDetailResponse(
      name = "unknown task",
      id = "testTaskId",
      application = "testApplication",
      buildTime = Instant.parse("2023-01-25T12:00:05.00Z"),
      startTime = Instant.parse("2023-01-25T12:00:05.00Z"),
      endTime = Instant.parse("2023-01-25T12:00:05.00Z"),
      status = TaskStatus.SUCCEEDED,
    )
    coEvery {
      orcaService.getApplicationTasks(
        "testApplication",
        limit = 100,
        statuses = "SUCCEEDED",
      )
    } returns listOf(
      task,
      task.copy(name = "verification (v1) (2023-01-25T12:00:05.00Z)"),
    )
    val currentState = resourceHandler.current(resource)
    assertEquals(currentState, DryRunResourceState("N/A"))

    // Get desired state
    val testEnvironment = Environment("testEnvironment", resources = setOf(resource))
    val testDeliveryArtifact = DryRunArtifact(
      "testDeliveryArtifactName",
      "testDeliveryConfigName",
      "testArtifactReference",
    )
    val testDeliveryConfig = DeliveryConfig(
      "testApplication",
      "testDeliveryConfigName",
      "testServiceAccount",
      artifacts = setOf(testDeliveryArtifact),
      environments = setOf(testEnvironment),
    )
    every { keelRepository.deliveryConfigFor(resource.id) } returns testDeliveryConfig
    every {
      keelRepository.latestVersionApprovedIn(
        testDeliveryConfig,
        testDeliveryArtifact,
        testEnvironment.name,
      )
    } returns "v1"
    val desiredState = resourceHandler.desired(resource)
    assertEquals(desiredState, DryRunResourceState("v1"))

    // Upsert the resource diff
    coEvery {
      taskLauncher.submitJob(
        resource,
        "deploy (testName-testArtifactReference) (v1)",
        "testName-testArtifactReference",
        listOf(
          OrcaJob(
            "wait",
            mapOf(
              "name" to "dryrun resource wait",
              "waitTime" to 300L,
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
      )
    } returns Task(
      "testTaskId",
      "testTaskName",
    )
    val diff = DefaultResourceDiff(desiredState, currentState)
    val tasks = resourceHandler.upsert(resource, diff)
    assertEquals(1, tasks.size)

    // Verify sequence of deployment events
    verifySequence {
      eventPublisher.publishEvent(ArtifactVersionDeployed(resource.id, "N/A"))
      eventPublisher.publishEvent(ArtifactVersionDeploying(resource.id, "v1"))
    }
  }

  @Test
  fun testNoVersionAvailable() = runBlocking {
    // Set up DryRunResourceHandler
    val eventPublisher = mockk<EventPublisher>()
    val keelRepository = mockk<KeelRepository>()
    val taskLauncher = mockk<TaskLauncher>()
    val orcaService = mockk<OrcaService>()
    val dryRunTasks = DryRunTasks()
    val resourceHandler = DryRunResourceHandler(
      eventPublisher,
      keelRepository,
      taskLauncher,
      orcaService,
      dryRunTasks,
    )

    // Given resource
    val resourceSpec = DryRunResourceSpec(
      "testArtifactReference",
      "testName",
      waitTime = Duration.ofMinutes(5),
      fail = true,
      metadata = mapOf("application" to "testApplication"),
    )
    val resource = Resource(
      resourceHandler.supportedKind.kind,
      mapOf(
        "id" to "testId",
        "application" to "testApplication",
      ),
      resourceSpec,
    )

    // Given environment, artifact, delivery config
    val testEnvironment = Environment("testEnvironment", resources = setOf(resource))
    val testDeliveryArtifact = DryRunArtifact(
      "testDeliveryArtifactName",
      "testDeliveryConfigName",
      "testArtifactReference",
    )
    val testDeliveryConfig = DeliveryConfig(
      "testApplication",
      "testDeliveryConfigName",
      "testServiceAccount",
      artifacts = setOf(testDeliveryArtifact),
      environments = setOf(testEnvironment),
    )

    every { keelRepository.deliveryConfigFor(resource.id) } returns testDeliveryConfig
    every {
      keelRepository.latestVersionApprovedIn(
        testDeliveryConfig,
        testDeliveryArtifact,
        testEnvironment.name,
      )
    } returns null

    // Verify exception is thrown if no artifact version available
    val noVersionAvailableException = assertThrows(NoVersionAvailable::class.java) {
      runBlocking {
        resourceHandler.desired(resource)
      }
    }
    assertEquals(
      noVersionAvailableException.message,
      "No version available for deployment with name, testDeliveryArtifactName, " +
        "and type, osoriano/dry-run-artifact@v1",
    )
    assertTrue(noVersionAvailableException is ResourceCurrentlyUnresolvable)
  }

  @Test
  fun testEnvironmentNotFoundForResource() = runBlocking {
    // Set up DryRunResourceHandler
    val eventPublisher = mockk<EventPublisher>()
    val keelRepository = mockk<KeelRepository>()
    val taskLauncher = mockk<TaskLauncher>()
    val orcaService = mockk<OrcaService>()
    val dryRunTasks = DryRunTasks()
    val resourceHandler = DryRunResourceHandler(
      eventPublisher,
      keelRepository,
      taskLauncher,
      orcaService,
      dryRunTasks,
    )

    // Given resource
    val resourceSpec = DryRunResourceSpec(
      "testArtifactReference",
      "testName",
      waitTime = Duration.ofMinutes(5),
      fail = true,
      metadata = mapOf("application" to "testApplication"),
    )
    val resource = Resource(
      resourceHandler.supportedKind.kind,
      mapOf(
        "id" to "testId",
        "application" to "testApplication",
      ),
      resourceSpec,
    )

    // Given artifact, delivery config
    val testDeliveryArtifact = DryRunArtifact(
      "testDeliveryArtifactName",
      "testDeliveryConfigName",
      "testArtifactReference",
    )
    val testDeliveryConfig = DeliveryConfig(
      "testApplication",
      "testDeliveryConfigName",
      "testServiceAccount",
      artifacts = setOf(testDeliveryArtifact),
    )

    every { keelRepository.deliveryConfigFor(resource.id) } returns testDeliveryConfig

    // Verify exception is thrown
    val envNotFoundError = assertThrows(EnvironmentNotFoundException::class.java) {
      runBlocking {
        resourceHandler.desired(resource)
      }
    }

    val expectedErrorMessage = """
      No environment with resource id: testId is found in delivery config environments
      DeliveryConfig(application=testApplication, name=testDeliveryConfigName, serviceAccount=testServiceAccount,
      artifacts=[DryRunArtifact(name=testDeliveryArtifactName, deliveryConfigName=testDeliveryConfigName, reference=testArtifactReference)], environments=[], previewEnvironments=[],
      apiVersion=delivery.config.spinnaker.netflix.com/v1, metadata={}, rawConfig=null, updatedAt=null)
    """.trimIndent().replace("\n", " ")
    assertEquals(
      expectedErrorMessage,
      envNotFoundError.message,
    )
    assertTrue(envNotFoundError is NoSuchEntityException)
  }
}
