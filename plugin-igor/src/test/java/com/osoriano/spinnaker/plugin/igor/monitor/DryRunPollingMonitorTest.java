package com.osoriano.spinnaker.plugin.igor.monitor;

import static com.osoriano.spinnaker.plugin.igor.monitor.DryRunPollingMonitor.DRYRUN_ARTIFACT_TYPE;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.keel.KeelService;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.osoriano.spinnaker.plugin.igor.cache.DryRunCache;
import com.osoriano.spinnaker.plugin.igor.config.DryRunPollingConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

public class DryRunPollingMonitorTest {
  private Registry registry = new DefaultRegistry();
  private DynamicConfigService dynamicConfigService = null;
  private DiscoveryStatusListener discoveryStatusListener = null;
  private TaskScheduler scheduler = mock(TaskScheduler.class);
  private DryRunCache cache = mock(DryRunCache.class);
  private KeelService keelService = mock(KeelService.class);

  private IgorConfigurationProperties igorConfigurationProperties =
      new IgorConfigurationProperties();

  private ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @SuppressWarnings("unchecked")
  public void poll_SubmitsNewArtifact_OnlyWhenItIsTimeToPublish() {
    Duration publishInterval = Duration.ofSeconds(60);
    int numberOfUniqueArtifacts = 2;
    String artifactPrefix = "artifactPrefix";
    int indexPadLength = 1;

    DryRunPollingConfig dryRunPollingConfig =
        new DryRunPollingConfig(
            publishInterval, numberOfUniqueArtifacts, artifactPrefix, indexPadLength);

    DryRunPollingMonitor dryRunPollingMonitor =
        new DryRunPollingMonitor(
            igorConfigurationProperties,
            registry,
            dynamicConfigService,
            discoveryStatusListener,
            Optional.empty(),
            scheduler,
            cache,
            keelService,
            dryRunPollingConfig);

    String expectedArtifact1 = "artifactPrefix1";
    String expectedArtifact2 = "artifactPrefix2";

    // expectedArtifact1 should publish a new version after the poll
    when(cache.getCacheValue(expectedArtifact1)).thenReturn("0");
    // expectedArtifact2 should NOT publish new version since the cached timestamp == now
    when(cache.getCacheValue(expectedArtifact2))
        .thenReturn(String.valueOf(Instant.now().toEpochMilli()));

    // Call poll()
    dryRunPollingMonitor.poll(false);

    // Validate artifactEvent for expectedArtifact1 is submitted to Keel
    ArgumentCaptor<Map<String, Object>> artifactEventCaptor = ArgumentCaptor.forClass(Map.class);
    verify(keelService, times(1)).sendArtifactEvent(artifactEventCaptor.capture());

    Map<String, Object> payloadMap =
        objectMapper.convertValue(artifactEventCaptor.getValue().get("payload"), Map.class);
    List<Map<String, Object>> artifactList =
        objectMapper.convertValue(payloadMap.get("artifacts"), List.class);

    assertEquals(artifactList.size(), 1);
    assertEquals(artifactList.get(0).get("name"), expectedArtifact1);
    assertEquals(artifactList.get(0).get("type"), DRYRUN_ARTIFACT_TYPE);

    // Validate expected value is cached
    verify(cache).setCacheValue(eq(expectedArtifact1), isA(String.class));
  }
}
