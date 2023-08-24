package com.osoriano.spinnaker.plugin.verification.dryrun

import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.plugins.VerificationEvaluator
import com.netflix.spinnaker.keel.model.OrcaJob
import com.osoriano.spinnaker.plugin.rollback.RollbackHandler
import com.osoriano.spinnaker.plugin.task.DryRunTasks
import com.osoriano.spinnaker.plugin.verification.base.OrcaTaskVerificationEvaluator
import com.osoriano.spinnaker.plugin.verification.config.VerificationConfig
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component

/** A dry run verification evaluator with configurable behavior */
@EnableConfigurationProperties(VerificationConfig::class)
@Component
class DryRunVerificationEvaluator(
  taskLauncher: TaskLauncher,
  rollbackHandler: RollbackHandler,
  verificationConfig: VerificationConfig,
  private val dryRunTasks: DryRunTasks,
) : OrcaTaskVerificationEvaluator<DryRunVerification>(
  DRY_RUN_VERIFICATION_V1,
  DryRunVerification::class.java,
  taskLauncher,
  rollbackHandler,
  verificationConfig,
),
  VerificationEvaluator<DryRunVerification> {

  override fun getOrcaVerificationStages(
    context: ArtifactInEnvironmentContext,
    verification: DryRunVerification,
  ): List<OrcaJob> {
    return dryRunTasks.createDryRunTask(
      "dryrun verification wait",
      verification.waitTime,
      verification.fail,
    )
  }

  override fun getOrcaTaskDescription(
    context: ArtifactInEnvironmentContext,
    verification: DryRunVerification,
  ): String {
    return "verify (${context.environmentName}) (${context.version})"
  }
}
