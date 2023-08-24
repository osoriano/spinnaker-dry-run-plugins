package com.osoriano.spinnaker.plugin.rollback

import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.core.api.PromotionStatus
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.services.ApplicationService
import com.osoriano.spinnaker.plugin.artifact.dryrun.DryRunArtifact
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows

class RollbackHandlerTest {
  val ENVIRONMENT_NAME = "testEnvironment"
  val ARTIFACT_REFERENCE = "testArtifactReference"
  val VERSION = "testVersion"

  @Test
  fun testRollbackVersionInEnvironment() {
    // Set up RollbackHandler
    val applicationService = mockk<ApplicationService>()
    val keelRepository = mockk<KeelRepository>()

    val rollbackHandler = RollbackHandler(
      applicationService,
      keelRepository,
    )

    // Given environment, artifact
    val environment = Environment(ENVIRONMENT_NAME)
    val artifact = DryRunArtifact(
      "testDeliveryArtifact",
      "testDeliveryConfig",
      ARTIFACT_REFERENCE,
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
      ENVIRONMENT_NAME,
      ARTIFACT_REFERENCE,
      VERSION,
    )

    // Mock response for environment not pinned
    every {
      keelRepository.getPinnedVersion(deliveryConfig, ENVIRONMENT_NAME, ARTIFACT_REFERENCE)
    } returns null

    // Mock veto response
    every {
      applicationService.markAsVetoedIn(
        user = "Spinnaker",
        application = "testApplication",
        veto = EnvironmentArtifactVeto(
          targetEnvironment = "testEnvironment",
          reference = ARTIFACT_REFERENCE,
          version = VERSION,
          vetoedBy = "Spinnaker",
          comment = "Failed verification (testVersion)",
        ),
        force = false,
      )
    } returns Unit

    // Mock responses to get last successfully deployed version
    every {
      keelRepository.getArtifactVersionByPromotionStatus(
        deliveryConfig,
        ENVIRONMENT_NAME,
        context.artifact,
        PromotionStatus.PREVIOUS,
      )
    } returns PublishedArtifact(
      name = artifact.name,
      type = artifact.type,
      reference = artifact.reference,
      version = "lastSuccessfulVersion",
    )

    // Mock response for pinning last successful version
    every {
      applicationService.pin(
        user = "Spinnaker",
        application = "testApplication",
        pin = EnvironmentArtifactPin(
          targetEnvironment = "testEnvironment",
          reference = "testArtifactReference",
          version = "lastSuccessfulVersion",
          comment = "Pin last successful (lastSuccessfulVersion) " +
            "due to failed verification (testVersion)",
          pinnedBy = "Spinnaker",
        ),
      )
    } returns Unit

    // Ensure failure is handle with veto and pinning
    rollbackHandler.handleFailure(
      context = context,
      RollbackBehavior.LAST_SUCCESSFUL,
    )
  }

  @Test
  fun testRollbackVersionInEnvironmentNoRollbackVersion() {
    // Set up RollbackHandler
    val applicationService = mockk<ApplicationService>()
    val keelRepository = mockk<KeelRepository>()

    val rollbackHandler = RollbackHandler(
      applicationService,
      keelRepository,
    )

    // Given environment, artifact
    val environment = Environment("testEnvironment")
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

    // Mock response for environment not pinned
    every {
      keelRepository.getPinnedVersion(deliveryConfig, ENVIRONMENT_NAME, ARTIFACT_REFERENCE)
    } returns null

    // Mock veto response
    every {
      applicationService.markAsVetoedIn(
        user = "Spinnaker",
        application = "testApplication",
        veto = EnvironmentArtifactVeto(
          targetEnvironment = "testEnvironment",
          reference = ARTIFACT_REFERENCE,
          version = VERSION,
          vetoedBy = "Spinnaker",
          comment = "Failed verification (testVersion)",
        ),
        force = false,
      )
    } returns Unit

    // Mock response for no last successfully deployed version
    every {
      keelRepository.getArtifactVersionByPromotionStatus(
        deliveryConfig,
        ENVIRONMENT_NAME,
        context.artifact,
        PromotionStatus.PREVIOUS,
      )
    } returns null

    // Ensure failure is handle with veto and pinning
    val noSuchVersionException = assertThrows(NoSuchVersion::class.java) {
      rollbackHandler.handleFailure(
        context = context,
        RollbackBehavior.LAST_SUCCESSFUL,
      )
    }
    assertEquals(
      noSuchVersionException.message,
      "No successful rollback version for env " +
        "testApplication/testEnvironment",
    )
  }
}
