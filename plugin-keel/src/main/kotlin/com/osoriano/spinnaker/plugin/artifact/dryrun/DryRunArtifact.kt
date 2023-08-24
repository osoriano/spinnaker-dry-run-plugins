package com.osoriano.spinnaker.plugin.artifact.dryrun

import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.artifacts.CreatedAtSortingStrategy

const val DRY_RUN_ARTIFACT_V1 = "osoriano/dry-run-artifact@v1"

/**
 * A DeliveryArtifact that describes dryrun artifacts, as defined in a DeliveryConfig.
 *
 * <p>Unlike other places within Spinnaker, this class does not describe a specific instance of a
 * software artifact (i.e. the output of a build that is published to an artifact repository), but
 * rather the high-level properties that allow keel and ArtifactSupplier plugins to find/process the
 * actual artifacts.
 */
data class DryRunArtifact(
  override val name: String,
  override val deliveryConfigName: String? = null,
  override val reference: String = name,
) : DeliveryArtifact() {

  override val type = DRY_RUN_ARTIFACT_V1

  override val sortingStrategy = CreatedAtSortingStrategy

  override fun withDeliveryConfigName(deliveryConfigName: String): DeliveryArtifact {
    return this.copy(deliveryConfigName = deliveryConfigName)
  }
}
