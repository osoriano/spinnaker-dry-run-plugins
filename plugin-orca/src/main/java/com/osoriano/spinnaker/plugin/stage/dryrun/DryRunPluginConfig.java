package com.osoriano.spinnaker.plugin.stage.dryrun;

import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

/** Data in this class maps to the plugin configuration in a service's config YAML */
@ConstructorBinding
@ConfigurationProperties(
    "spinnaker.extensibility.plugins.osoriano.spinnakerdryrunplugin.config.stage")
@AllArgsConstructor
@Data
public class DryRunPluginConfig {
  private Duration backoffPeriod;
  private Duration timeout;
}
