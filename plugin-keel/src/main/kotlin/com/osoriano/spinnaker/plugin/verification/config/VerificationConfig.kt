package com.osoriano.spinnaker.plugin.verification.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

/** Data in this class maps to the plugin configuration in a service's config YAML */
@ConstructorBinding
@ConfigurationProperties("spinnaker.extensibility.plugins.osoriano.spinnakerdryrunplugin.config.verification")
data class VerificationConfig(
  val maxRetries: Int,
)
