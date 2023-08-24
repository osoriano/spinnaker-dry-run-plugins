package com.osoriano.spinnaker.plugin.artifact.dryrun

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.support.EventPublisher
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.Clock
import java.time.Duration
import java.time.Instant

class DryRunArtifactSupplierTest {

  @Test
  fun testGetArtifacts() {
    // Set up DryRunArtifactSupplier
    val eventPublisher = mockk<EventPublisher>()

    val publishInterval = Duration.ofMinutes(5)
    val dryRunArtifactConfig = DryRunArtifactConfig(publishInterval)

    val numPublishIntervals = 2
    val baseInstant = Instant.parse("2023-01-25T12:00:00.00Z")
    val nextInstant = baseInstant.plus(
      publishInterval.multipliedBy(numPublishIntervals.toLong()),
    )
    val clock = mockk<Clock>()
    every { clock.instant() } returnsMany listOf(nextInstant, baseInstant)

    val dryRunArtifactSupplier = DryRunArtifactSupplier(
      eventPublisher,
      dryRunArtifactConfig,
      clock,
    )

    // Get artifacts given the test data
    val testDeliveryConfig = DeliveryConfig(
      "testApplication",
      "testDeliveryConfigName",
      "testServiceAccount",
    )
    val testDeliveryArtifact = DryRunArtifact(
      "testDeliveryArtifactName",
      "testDeliveryConfigName",
      "testArtifactReference",
    )
    val testArtifactLimit = 5
    val actualArtifacts = dryRunArtifactSupplier.getLatestArtifacts(
      testDeliveryConfig,
      testDeliveryArtifact,
      testArtifactLimit,
    )

    // Expect 3 artifacts (published at 12:00, 12:05, and 12:10)
    assertEquals(numPublishIntervals + 1, actualArtifacts.size)
    assertEquals(baseInstant.plus(Duration.ofMinutes(10)), actualArtifacts[0].createdAt)
    assertEquals(baseInstant.plus(Duration.ofMinutes(5)), actualArtifacts[1].createdAt)
    assertEquals(baseInstant, actualArtifacts[2].createdAt)
  }
}
