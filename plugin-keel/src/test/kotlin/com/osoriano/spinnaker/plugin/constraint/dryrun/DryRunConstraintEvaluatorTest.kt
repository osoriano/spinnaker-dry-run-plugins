package com.osoriano.spinnaker.plugin.constraint.dryrun

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.constraints.ConstraintRepository
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.osoriano.spinnaker.plugin.artifact.dryrun.DryRunArtifact
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.Clock
import java.time.Duration
import java.time.Instant

class DryRunConstraintEvaluatorTest {

  @Test
  fun testConstraintEvaluator() {
    // Set up DryRunConstraintEvaluator
    val repository = mockk<ConstraintRepository>()
    val eventPublisher = mockk<EventPublisher>()
    val clock = mockk<Clock>()
    val constraintEvaluator = DryRunConstraintEvaluator(
      repository,
      eventPublisher,
      clock,
    )

    // Given constraint, environment, artifact
    val constraint = DryRunConstraint(
      waitTime = Duration.ofSeconds(30),
      fail = false,
      alternate = true,
      alternateInterval = Duration.ofSeconds(60),
    )
    val environment = Environment(
      "testEnvironment",
      constraints = setOf(constraint),
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
    val state = ConstraintState(
      deliveryConfigName = "testDeliveryConfig",
      environmentName = "testEnvironment",
      artifactVersion = "testVersion",
      artifactReference = "testArtifactReference",
      type = DRY_RUN_CONSTRAINT_V1,
      status = ConstraintStatus.PENDING,
      createdAt = Instant.parse("2023-01-25T12:00:00.00Z"),
    )

    // Initially, state is pending
    // Only the judgedAt and judgedByState is updated
    every { clock.instant() } returns Instant.parse("2023-01-25T12:00:05.00Z")
    every {
      repository.storeConstraintState(
        state.copy(
          judgedAt = Instant.parse("2023-01-25T12:00:05.00Z"),
          judgedBy = "Spinnaker",
        ),
      )
    } returns Unit

    val canPromotePending = constraintEvaluator.canPromote(
      artifact,
      "testVersion",
      deliveryConfig,
      environment,
      constraint,
      state,
    )
    assertEquals(canPromotePending, false)

    // After wait time completes, state is passing
    every { clock.instant() } returns Instant.parse("2023-01-25T12:00:35.00Z")
    every {
      repository.storeConstraintState(
        state.copy(
          attributes = DryRunConstraintAttributes(
            waitTime = Duration.ofSeconds(30),
            fail = false,
            alternate = true,
            alternateInterval = Duration.ofSeconds(60),
          ),
          status = ConstraintStatus.PASS,
          judgedAt = Instant.parse("2023-01-25T12:00:35.00Z"),
          judgedBy = "Spinnaker",
        ),
      )
    } returns Unit
    val canPromotePassing = constraintEvaluator.canPromote(
      artifact,
      "testVersion",
      deliveryConfig,
      environment,
      constraint,
      state,
    )
    assertEquals(canPromotePassing, true)

    // After wait time and alternate interval, state flips to failed
    every { clock.instant() } returns Instant.parse("2023-01-25T12:01:35.00Z")
    every {
      repository.storeConstraintState(
        state.copy(
          attributes = DryRunConstraintAttributes(
            waitTime = Duration.ofSeconds(30),
            fail = false,
            alternate = true,
            alternateInterval = Duration.ofSeconds(60),
          ),
          status = ConstraintStatus.FAIL,
          judgedAt = Instant.parse("2023-01-25T12:01:35.00Z"),
          judgedBy = "Spinnaker",
        ),
      )
    } returns Unit
    val canPromoteFailing = constraintEvaluator.canPromote(
      artifact,
      "testVersion",
      deliveryConfig,
      environment,
      constraint,
      state,
    )
    assertEquals(canPromoteFailing, false)
  }
}
