package com.osoriano.spinnaker.plugin.constraint.dryrun

import com.netflix.spinnaker.keel.api.StatefulConstraint
import java.time.Duration

const val DRY_RUN_CONSTRAINT_V1 = "osoriano/dry-run-constraint@v1"

/** A dry run constraint, as defined in a DeliveryConfig. */
data class DryRunConstraint(
  /* Time to wait before completing the PENDING state */
  val waitTime: Duration,
  /* If true, sets the state to FAIL after the wait time.
   * Otherwise, sets the state to PASS.*/
  val fail: Boolean,
  /* If true, alternate between pass and fail state
   * after the waitTime. */
  val alternate: Boolean,
  /* Interval for alternating between pass and fail states */
  val alternateInterval: Duration = waitTime,
) : StatefulConstraint(DRY_RUN_CONSTRAINT_V1)
