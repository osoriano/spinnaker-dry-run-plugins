package com.osoriano.spinnaker.plugin.constraint.dryrun

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintRepository
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.FAIL
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.api.constraints.StatefulConstraintEvaluator
import com.netflix.spinnaker.keel.api.constraints.SupportedConstraintAttributesType
import com.netflix.spinnaker.keel.api.constraints.SupportedConstraintType
import com.netflix.spinnaker.keel.api.support.EventPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

@Component
class DryRunConstraintEvaluator(
  override val repository: ConstraintRepository,
  override val eventPublisher: EventPublisher,
  private val clock: Clock,
) : StatefulConstraintEvaluator<DryRunConstraint, DryRunConstraintAttributes> {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override val supportedType = SupportedConstraintType<DryRunConstraint>(DRY_RUN_CONSTRAINT_V1)
  override val attributeType = SupportedConstraintAttributesType<DryRunConstraintAttributes>(DRY_RUN_CONSTRAINT_V1)

  /**
   * Return whether the artifact can be promoted according to the context
   *
   * Also manages the constraint state, which is persisted per context.
   */
  override fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment,
    constraint: DryRunConstraint,
    state: ConstraintState,
  ): Boolean {
    val status = computeStatus(constraint, state)
    log.info(
      "Constraint status for {}/{}/{} is {}",
      deliveryConfig.application,
      targetEnvironment,
      version,
      status,
    )

    if (status != state.status) {
      val attributes = DryRunConstraintAttributes(
        waitTime = constraint.waitTime,
        fail = constraint.fail,
        alternate = constraint.alternate,
        alternateInterval = constraint.alternateInterval,
      )
      repository.storeConstraintState(
        state.copy(
          attributes = attributes,
          status = status,
          judgedAt = clock.instant(),
          judgedBy = "Spinnaker",
        ),
      )
    } else {
      // Update the judged time so it's clear when we last checked
      repository.storeConstraintState(state.copy(judgedAt = clock.instant(), judgedBy = "Spinnaker"))
    }

    return status == PASS
  }

  private fun computeStatus(
    constraint: DryRunConstraint,
    state: ConstraintState,
  ): ConstraintStatus {
    val elapsed = Duration.between(state.createdAt, clock.instant())
    val completedWaitTime = elapsed.compareTo(constraint.waitTime) >= 0

    if (!completedWaitTime) {
      return PENDING
    }

    if (!constraint.alternate) {
      if (constraint.fail) {
        return FAIL
      }
      return PASS
    }

    val elapsedSinceWaitTime = elapsed.minus(constraint.waitTime)
    val numIntervals = elapsedSinceWaitTime.dividedBy(constraint.alternateInterval)
    if (numIntervals % 2 == 0L) {
      // Even intervals
      if (constraint.fail) {
        return FAIL
      }
      return PASS
    }
    // Odd intervals
    if (constraint.fail) {
      return PASS
    }
    return FAIL
  }

  /**
   * We want this constraint to be able to flip the status from pass to fail
   */
  override fun shouldAlwaysReevaluate(): Boolean = true
}
