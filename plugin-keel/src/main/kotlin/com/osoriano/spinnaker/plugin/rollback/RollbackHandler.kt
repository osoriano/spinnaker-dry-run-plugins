package com.osoriano.spinnaker.plugin.rollback

import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.core.api.PromotionStatus
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.services.ApplicationService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RollbackHandler(
  private val applicationService: ApplicationService,
  private val keelRepository: KeelRepository,
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  /**
   * Handle the potential rollback of an environment version due to a failure.
   */
  fun handleFailure(
    context: ArtifactInEnvironmentContext,
    rollbackBehavior: RollbackBehavior,
  ) {
    if (rollbackBehavior == RollbackBehavior.NONE) {
      // No rollback action to take
      return
    }
    val application = context.deliveryConfig.application
    val environmentName = context.environmentName

    val pinnedVersion = keelRepository.getPinnedVersion(context.deliveryConfig, context.environmentName, context.artifactReference)
    val isPinned = !pinnedVersion.isNullOrEmpty()

    if (isPinned) {
      log.info("skipping rollback of pinned env: $application/$environmentName")
      return
    }
    log.info("handling rollback for env: $application/$environmentName/${context.version}")

    val veto = EnvironmentArtifactVeto(
      targetEnvironment = environmentName,
      reference = context.artifactReference,
      version = context.version,
      vetoedBy = "Spinnaker",
      comment = "Failed verification (${context.version})",
    )
    applicationService.markAsVetoedIn(
      user = "Spinnaker",
      application = application,
      veto = veto,
      force = false,
    )

    if (rollbackBehavior == RollbackBehavior.LAST_SUCCESSFUL) {
      val lastSucceeded = keelRepository.getArtifactVersionByPromotionStatus(
        context.deliveryConfig,
        context.environmentName,
        context.artifact,
        PromotionStatus.PREVIOUS,
      ) ?: throw NoSuchVersion("No successful rollback version for env $application/$environmentName")

      val lastSucceededVersion = lastSucceeded.version
      val pin = EnvironmentArtifactPin(
        targetEnvironment = environmentName,
        reference = context.artifactReference,
        version = lastSucceeded.version,
        comment = "Pin last successful ($lastSucceededVersion) " +
          "due to failed verification (${context.version})",
        pinnedBy = "Spinnaker",
      )
      applicationService.pin(
        user = "Spinnaker",
        application = application,
        pin = pin,
      )
    }
  }
}
