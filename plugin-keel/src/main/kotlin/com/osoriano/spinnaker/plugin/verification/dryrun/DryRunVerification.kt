package com.osoriano.spinnaker.plugin.verification.dryrun

import com.osoriano.spinnaker.plugin.rollback.RollbackBehavior
import com.osoriano.spinnaker.plugin.verification.base.AutoRollbackVerification
import java.time.Duration

const val DRY_RUN_VERIFICATION_V1 = "osoriano/dry-run-verification@v1"

/** A dry run verification, as defined in a DeliveryConfig. */
data class DryRunVerification(
  val waitTime: Duration,
  val fail: Boolean,
  override val rollbackBehavior: RollbackBehavior,
) : AutoRollbackVerification() {

  override val type = DRY_RUN_VERIFICATION_V1
  override val id = DRY_RUN_VERIFICATION_V1
}
