package com.osoriano.spinnaker.plugin.resource.dryrun

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.plugins.kind
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.orca.ExecutionDetailResponse
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.ArtifactNotFoundException
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.osoriano.spinnaker.plugin.artifact.dryrun.DryRunArtifact
import com.osoriano.spinnaker.plugin.resource.exceptions.EnvironmentNotFoundException
import com.osoriano.spinnaker.plugin.resource.exceptions.NoVersionAvailable
import com.osoriano.spinnaker.plugin.task.DryRunTasks
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DryRunResourceHandler(
  override val eventPublisher: EventPublisher,
  private val keelRepository: KeelRepository,
  private val taskLauncher: TaskLauncher,
  private val orcaService: OrcaService,
  private val dryRunTasks: DryRunTasks,
) : ResourceHandler<DryRunResourceSpec, DryRunResourceState> {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override val supportedKind = kind<DryRunResourceSpec>("osoriano/dry-run-resource@v1")

  /**
   * Resolve and convert the resource spec into the type that represents the diff-able desired
   * state.
   *
   * @param resource the resource as persisted in the Keel database.
   */
  override suspend fun desired(resource: Resource<DryRunResourceSpec>): DryRunResourceState {
    val deliveryConfig = keelRepository.deliveryConfigFor(resource.id)
    val artifact = getArtifact(resource, deliveryConfig)

    val environment =
      deliveryConfig.environments.find { e -> e.resources.any { r -> r.id == resource.id } }
        ?: throw EnvironmentNotFoundException(resource.id, deliveryConfig.toString())
    val version =
      keelRepository.latestVersionApprovedIn(deliveryConfig, artifact, environment.name)
    log.debug("Resource {} has desired state {}", resource.id, version)

    if (version == null) {
      throw NoVersionAvailable(artifact.name, artifact.type)
    }
    return DryRunResourceState(version)
  }

  /**
   * Get the artifact referenced in the resource spec
   */
  private fun getArtifact(
    resource: Resource<DryRunResourceSpec>,
    deliveryConfig: DeliveryConfig,
  ): DryRunArtifact {
    val artifactReference = resource.spec.artifactReference

    val artifactInConfig = deliveryConfig.artifacts.find { artifactReference.equals(it.reference) }
    if (artifactInConfig == null) {
      throw ArtifactNotFoundException(artifactReference, deliveryConfig.name)
    }

    require(artifactInConfig is DryRunArtifact) {
      "Expected artifact of type DryRunArtifact but got: ${artifactInConfig::class}"
    }
    return artifactInConfig
  }

  /**
   * Return the current actual representation of what resource looks like in the cloud.
   */
  override suspend fun current(resource: Resource<DryRunResourceSpec>): DryRunResourceState {
    val tasks = orcaService.getApplicationTasks(resource.application, limit = 100, statuses = "SUCCEEDED")
    val correlationId = resource.spec.id
    val currentVersion = tasks.let {
      for (task in tasks) {
        val version = getVersionFromTask(task, correlationId)
        if (version != null) {
          return@let version
        }
      }
      null
    } ?: "N/A"

    val currentState = DryRunResourceState(currentVersion)

    log.debug(
      "Resource {} has current state {}",
      resource.id,
      currentState.version,
    )
    notifyArtifactDeployed(resource, currentState.version)

    return currentState
  }

  private fun getVersionFromTask(task: ExecutionDetailResponse, correlationId: String): String? {
    val nameParts = task.name.split(' ')
    if (nameParts.size != 3) {
      return null
    }

    val action = nameParts[0]
    if (action != "deploy") {
      return null
    }

    val taskResourceId = nameParts[1].trim('(', ')')
    if (taskResourceId != correlationId) {
      return null
    }

    val version = nameParts[2].trim('(', ')')
    return version
  }

  /**
   * Create or update a resource so that it matches the desired state
   *
   * @return a list of tasks launched to actuate the resource.
   */
  override suspend fun upsert(
    resource: Resource<DryRunResourceSpec>,
    resourceDiff: ResourceDiff<DryRunResourceState>,
  ): List<Task> {
    val currentVersion = resourceDiff.current?.version
    val desiredVersion = resourceDiff.desired.version
    if (desiredVersion == currentVersion) {
      log.info("Resource {} is in sync for version {}", resource.id, currentVersion)
      return emptyList()
    }

    log.info(
      "Updating resource {} from {} to {}",
      resource.id,
      currentVersion,
      desiredVersion,
    )

    val stages = dryRunTasks.createDryRunTask(
      "dryrun resource wait",
      resource.spec.waitTime,
      resource.spec.fail,
    )

    notifyArtifactDeploying(resource, desiredVersion)

    if (stages.isEmpty()) {
      return emptyList()
    }

    val correlationId = resource.spec.id
    return listOf(
      taskLauncher.submitJob(
        resource,
        "deploy ($correlationId) ($desiredVersion)",
        correlationId,
        stages,
      ),
    )
  }

  /**
   * Whether or not this resource is still busy running a previous actuation
   */
  override suspend fun actuationInProgress(resource: Resource<DryRunResourceSpec>): Boolean {
    // For explanation of correlationId, see:
    // https://github.com/spinnaker/keel/pull/1817
    val correlationId = resource.spec.id
    return taskLauncher.correlatedTasksRunning(correlationId)
  }
}
