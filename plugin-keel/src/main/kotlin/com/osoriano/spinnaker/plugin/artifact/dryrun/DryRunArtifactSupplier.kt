package com.osoriano.spinnaker.plugin.artifact.dryrun

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.ArtifactMetadata
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.Commit
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.Job
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.Repo
import com.netflix.spinnaker.keel.api.artifacts.SortingStrategy
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.plugins.SupportedArtifact
import com.netflix.spinnaker.keel.api.plugins.SupportedSortingStrategy
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.artifacts.CreatedAtSortingStrategy
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Keel plugin implementation of ArtifactSupplier for dryrun artifacts.
 *
 * <p>Provides artifact versions to Keel so they can be persisted and evaluated for promotion.
 */
@EnableConfigurationProperties(DryRunArtifactConfig::class)
@Component
class DryRunArtifactSupplier(
  override val eventPublisher: EventPublisher,
  private val dryRunArtifactConfig: DryRunArtifactConfig,
  private val clock: Clock,
  private val objectMapper: ObjectMapper = ObjectMapper(),
) : ArtifactSupplier<DryRunArtifact, CreatedAtSortingStrategy> {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val publishInterval = dryRunArtifactConfig.publishInterval

  private val baseArtifactTime by lazy {
    val now = clock.instant().getEpochSecond()
    // Round down to the nearest publish interval
    // For example, if publish interval is 15 minutes,
    // will round to one of 12:00, 12:15, 12:30, etc.
    val roundDownSeconds = publishInterval.getSeconds()
    Instant.ofEpochSecond(now - (now % roundDownSeconds))
  }

  override val supportedArtifact = SupportedArtifact(DRY_RUN_ARTIFACT_V1, DryRunArtifact::class.java)
  override val supportedSortingStrategy = SupportedSortingStrategy(DRY_RUN_ARTIFACT_V1, CreatedAtSortingStrategy::class.java)

  override fun getLatestArtifacts(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    limit: Int,
  ): List<PublishedArtifact> {
    return getLatestArtifacts(artifact, limit)
  }

  private fun getLatestArtifacts(
    artifact: DeliveryArtifact,
    limit: Int,
  ): List<PublishedArtifact> {
    require(artifact is DryRunArtifact) {
      "Expected artifact of type DryRunArtifact but got: ${artifact::class}"
    }

    val now = clock.instant()
    val elapsed = Duration.between(baseArtifactTime, now)
    val elapsedIntervals = elapsed.dividedBy(publishInterval)

    var artifactInstant =
      baseArtifactTime.plus(publishInterval.multipliedBy(elapsedIntervals))

    val publishedArtifacts = mutableListOf<PublishedArtifact>()
    while (!artifactInstant.isBefore(baseArtifactTime) && publishedArtifacts.size < limit) {
      val publishedArtifact = getPublishedArtifact(artifact, artifactInstant)
      publishedArtifacts.add(publishedArtifact)
      artifactInstant = artifactInstant.minus(publishInterval)
    }

    log.info(
      "Fetched {} {} artifacts with limit={} publishInterval={} baseTime={}",
      publishedArtifacts.size,
      artifact.name,
      limit,
      publishInterval,
      baseArtifactTime,
    )

    return publishedArtifacts
  }

  override fun getLatestArtifact(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
  ): PublishedArtifact? {
    return getLatestArtifacts(deliveryConfig, artifact, 1).firstOrNull()
  }

  override fun getArtifactByVersion(artifact: DeliveryArtifact, version: String): PublishedArtifact? {
    require(artifact is DryRunArtifact) {
      "Expected artifact of type DryRunArtifact but got: ${artifact::class}"
    }
    val artifactInstant = Instant.parse(version)
    // Artifact instant should be a multiple of publishInterval
    // since the first artifact is defined by baseArtifactTime
    if (artifactInstant.getEpochSecond() % publishInterval.getSeconds() != 0L) {
      return null
    }
    return getPublishedArtifact(artifact, artifactInstant)
  }

  override fun parseDefaultBuildMetadata(
    artifact: PublishedArtifact,
    sortingStrategy: SortingStrategy,
  ): BuildMetadata? {
    return artifact.buildMetadata
  }

  override fun parseDefaultGitMetadata(
    artifact: PublishedArtifact,
    sortingStrategy: SortingStrategy,
  ): GitMetadata? {
    return artifact.gitMetadata
  }

  /* Currently, we don't have any limitations for dryrun artifact versions */
  override fun shouldProcessArtifact(artifact: PublishedArtifact): Boolean {
    return true
  }

  override suspend fun getArtifactMetadata(
    artifact: PublishedArtifact,
  ): ArtifactMetadata? {
    log.info("GETTING METADATA!!! " + Gson().toJson(artifact))
    val timestamp = objectMapper.convertValue(artifact.metadata.get("createdAt"), Long::class.java)
    val artifactInstant = Instant.ofEpochMilli(timestamp)
    val commit = getCommitSha(artifactInstant)

    val result = ArtifactMetadata(
      artifact.buildMetadata ?: generateBuildMetadata(artifactInstant, commit),
      artifact.gitMetadata ?: generateGitMetadata(artifactInstant, commit),
    )

    return result
  }

  private fun generateBuildMetadata(artifactInstant: Instant, commit: String): BuildMetadata {
    val jobName = "dryrun artifact publisher"
    val jobNumber = 1908
    val jobLink = "https://jenkins-iso-vm.pinadmin.com/job/helloworlddummyservice-integration-tests/1908/"

    val publishDate = artifactInstant
    val publishDateString = publishDate.toString()
    val commitDate = artifactInstant
    val commitDateString = commitDate.toString()

    return BuildMetadata(
      id = jobNumber,
      number = commit,
      uid = commit,
      job = Job(
        name = jobName,
        link = jobLink,
      ),
      completedAt = publishDateString,
      startedAt = commitDateString,
      startedTs = commitDateString,
      completedTs = publishDateString,
      status = "SUCCESS",
    )
  }

  private fun generateGitMetadata(artifactInstant: Instant, commit: String): GitMetadata {
    val commitLink = "https://github.com/osoriano/spinnaker-dry-run-plugins/commit/deadbeef"
    val commitMessage =
      """
      published at $artifactInstant

      This is an example commit message
      """.trimIndent()

    return GitMetadata(
      commit = commit,
      author = "keel",
      project = "osoriano",
      branch = "main",
      repo = Repo(
        name = "spinnaker-dry-run-plugins",
        link = "https://github.com/osoriano/spinnaker-dry-run-plugins",
      ),
      commitInfo = Commit(
        commit,
        commitLink,
        commitMessage,
      ),
    )
  }

  /**
   * Return a PublishedArtifact.
   *
   * Since this is a dry run artifact based on a timestamp, much of
   * the content of the PublishedArtifact will be dry run data.
   */
  private fun getPublishedArtifact(
    dryRunArtifact: DryRunArtifact,
    artifactInstant: Instant,
  ): PublishedArtifact {
    val commit = getCommitSha(artifactInstant)
    val gitMetadata = generateGitMetadata(artifactInstant, commit)
    val buildMetadata = generateBuildMetadata(artifactInstant, commit)

    val metadata = mapOf<String, Any?>(
      "buildNumber" to commit,
      "commitId" to commit,
    )

    return PublishedArtifact(
      dryRunArtifact.name,
      DRY_RUN_ARTIFACT_V1,
      artifactInstant.toString(),
      createdAt = artifactInstant,
      gitMetadata = gitMetadata,
      buildMetadata = buildMetadata,
      metadata = metadata,
    )
  }

  private fun getCommitSha(artifactInstant: Instant): String {
    return DigestUtils.sha1Hex(artifactInstant.toString()).take(7)
  }
}
