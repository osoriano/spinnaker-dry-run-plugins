package com.osoriano.spinnaker.plugin.igor.monitor;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.keel.KeelService;
import com.netflix.spinnaker.igor.polling.CommonPollingMonitor;
import com.netflix.spinnaker.igor.polling.LockService;
import com.netflix.spinnaker.igor.polling.PollContext;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusChangeEvent;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.discovery.InstanceStatus;
import com.netflix.spinnaker.kork.discovery.RemoteStatusChangedEvent;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.osoriano.spinnaker.plugin.igor.cache.DryRunCache;
import com.osoriano.spinnaker.plugin.igor.config.DryRunPollingConfig;
import com.osoriano.spinnaker.plugin.igor.model.DryRunDelta;
import com.osoriano.spinnaker.plugin.igor.model.DryRunPollingDelta;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    "spinnaker.extensibility.plugins.osoriano.spinnakerdryrunplugin.config.artifact.igor.enabled")
@EnableConfigurationProperties(DryRunPollingConfig.class)
public class DryRunPollingMonitor extends CommonPollingMonitor<DryRunDelta, DryRunPollingDelta> {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  public static final String DRYRUN_ARTIFACT_TYPE = "osoriano/dry-run-artifact@v1";

  private final DryRunPollingConfig dryRunPollingConfig;
  private final DryRunCache cache;
  private final KeelService keelService;

  @Autowired
  public DryRunPollingMonitor(
      IgorConfigurationProperties properties,
      Registry registry,
      DynamicConfigService dynamicConfigService,
      DiscoveryStatusListener discoveryStatusListener,
      Optional<LockService> lockService,
      TaskScheduler scheduler,
      DryRunCache cache,
      KeelService keelService,
      DryRunPollingConfig dryRunPollingConfig) {
    super(
        properties,
        registry,
        dynamicConfigService,
        discoveryStatusListener,
        lockService,
        scheduler);
    this.cache = cache;
    this.keelService = keelService;
    this.dryRunPollingConfig = dryRunPollingConfig;
  }

  @PostConstruct
  public void start() {
    onApplicationEvent(
        new RemoteStatusChangedEvent(
            new DiscoveryStatusChangeEvent(InstanceStatus.UP, InstanceStatus.UP)));
  }

  @Override
  protected DryRunPollingDelta generateDelta(PollContext ctx) {
    String artifactName = ctx.partitionName;
    long publishIntervalSeconds = dryRunPollingConfig.getPublishInterval().toMillis();

    logger.debug("Checking dry run artifact: {}", artifactName);
    long lastPublishTimestamp = Long.parseLong(cache.getCacheValue(artifactName));
    long now = Instant.now().toEpochMilli();

    if (now - lastPublishTimestamp < publishIntervalSeconds) {
      logger.debug("Not yet time to publish dry run artifact: {}", artifactName);
      return new DryRunPollingDelta(List.of());
    }

    logger.info("Time to publish new dry run artifact version for {}", artifactName);
    DryRunDelta delta = new DryRunDelta(artifactName);

    return new DryRunPollingDelta(List.of(delta));
  }

  @Override
  protected void commitDelta(DryRunPollingDelta delta, boolean sendEvents) {
    long now = Instant.now().toEpochMilli();

    // There should only be 1 item in the delta but iterate through all just in case
    delta
        .getItems()
        .forEach(
            item -> {
              String artifactName = item.getArtifactName();
              submitKeelEvent(artifactName, now);
              cache.setCacheValue(artifactName, String.valueOf(now));
            });
  }

  private void submitKeelEvent(String artifactName, long timestamp) {
    Map<String, Object> metadata = Map.of("createdAt", timestamp);

    Artifact artifact =
        Artifact.builder()
            .type(DRYRUN_ARTIFACT_TYPE)
            .customKind(false)
            .name(artifactName)
            .version(String.valueOf(timestamp))
            .reference(artifactName)
            .metadata(metadata)
            .build();

    Map<String, Object> artifactEvent =
        Map.of(
            "payload",
            Map.of(
                "artifacts", List.of(artifact),
                "details", Map.of()),
            "eventName",
            "spinnaker_artifacts_dryrun");

    // TODO: look into submitting the event to Echo instead of Keel
    logger.info("Sending artifact event to Keel: {}", artifactEvent);
    AuthenticatedRequest.allowAnonymous(() -> keelService.sendArtifactEvent(artifactEvent));
  }

  @Override
  public void poll(boolean sendEvents) {
    buildArtifactNameList(dryRunPollingConfig)
        .forEach(artifact -> pollSingle(new PollContext(artifact, Map.of(), !sendEvents)));
  }

  @Override
  public String getName() {
    return "dryrunPollingMonitor";
  }

  private List<String> buildArtifactNameList(DryRunPollingConfig config) {
    List<String> result = new ArrayList<>();
    int numberOfArtifacts = config.getNumberOfUniqueArtifacts();

    for (int i = 1; i <= numberOfArtifacts; i++) {
      String suffix;
      if (numberOfArtifacts == 1) {
        suffix = "";
      } else {
        suffix = padStart(config.getIndexPadLength(), "0", String.valueOf(i));
      }
      result.add(config.getArtifactPrefix() + suffix);
    }

    return result;
  }

  private String padStart(int length, String padChar, String str) {
    if (str.length() >= length) {
      return str;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length - str.length(); i++) {
      sb.append(padChar);
    }
    sb.append(str);
    return sb.toString();
  }
}
