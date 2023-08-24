package com.osoriano.spinnaker.plugin.constraint.dryrun

import com.netflix.spinnaker.keel.api.constraints.ConstraintStateAttributes
import java.time.Duration

data class DryRunConstraintAttributes(
  /* Time to wait before completing the PENDING state */
  val waitTime: Duration,
  /* If true, sets the state to FAIL after the wait time.
   * Otherwise, sets the state to PASS.*/
  val fail: Boolean,
  /* If true, alternate between pass and fail state
   * after the waitTime. */
  val alternate: Boolean,
  /* Interval for alternating between pass and fail states */
  val alternateInterval: Duration,
) : ConstraintStateAttributes(DRY_RUN_CONSTRAINT_V1)
