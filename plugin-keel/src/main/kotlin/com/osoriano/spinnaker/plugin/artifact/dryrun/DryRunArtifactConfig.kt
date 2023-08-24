package com.osoriano.spinnaker.plugin.artifact.dryrun

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.time.Duration

/** Data in this class maps to the plugin configuration in a service's config YAML */
@ConstructorBinding
@ConfigurationProperties("spinnaker.extensibility.plugins.osoriano.spinnakerdryrunplugin.config.artifact")
data class DryRunArtifactConfig(
  val publishInterval: Duration,
)
