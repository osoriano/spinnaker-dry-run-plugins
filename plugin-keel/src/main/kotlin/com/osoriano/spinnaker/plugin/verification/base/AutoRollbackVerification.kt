package com.osoriano.spinnaker.plugin.verification.base

import com.netflix.spinnaker.keel.api.Verification
import com.osoriano.spinnaker.plugin.rollback.RollbackBehavior

abstract class AutoRollbackVerification() : Verification {
  abstract override val type: String
  abstract override val id: String
  abstract val rollbackBehavior: RollbackBehavior
}
