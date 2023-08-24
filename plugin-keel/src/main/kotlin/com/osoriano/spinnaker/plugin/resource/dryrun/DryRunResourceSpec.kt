package com.osoriano.spinnaker.plugin.resource.dryrun

import com.netflix.spinnaker.keel.api.ArtifactReferenceProvider
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.osoriano.spinnaker.plugin.artifact.dryrun.DRY_RUN_ARTIFACT_V1
import java.time.Duration

data class DryRunResourceSpec(
  override val artifactReference: String,
  val name: String,
  val waitTime: Duration,
  val fail: Boolean,
  val metadata: Map<String, String> = emptyMap(),
) : ResourceSpec, ArtifactReferenceProvider {

  override val artifactType = DRY_RUN_ARTIFACT_V1

  override val application: String
    get() = metadata.getValue("application")

  override val id: String
    get() = "$name-$artifactReference"

  override val displayName: String
    get() = "$name-$artifactReference"
}
