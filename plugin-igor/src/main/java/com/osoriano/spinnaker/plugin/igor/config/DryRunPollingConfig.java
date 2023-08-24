package com.osoriano.spinnaker.plugin.igor.config;

import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConstructorBinding
@ConfigurationProperties(
    "spinnaker.extensibility.plugins.osoriano.spinnakerdryrunplugin.config.artifact.igor")
@Data
@AllArgsConstructor
public class DryRunPollingConfig {
  private final Duration publishInterval;
  private final int numberOfUniqueArtifacts;
  private final String artifactPrefix;
  private final int indexPadLength;
}
