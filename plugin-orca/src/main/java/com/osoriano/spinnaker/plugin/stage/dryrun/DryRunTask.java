package com.osoriano.spinnaker.plugin.stage.dryrun;

import com.netflix.spinnaker.kork.plugins.api.spring.ExposeToApp;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Task to wait for a specified time */
@EnableConfigurationProperties(DryRunPluginConfig.class)
@Component
@ExposeToApp
public class DryRunTask implements RetryableTask {
  private static final Logger log = LoggerFactory.getLogger(DryRunTask.class);

  private DryRunPluginConfig config;
  private final Clock clock;

  DryRunTask(DryRunPluginConfig config, Clock clock) {
    this.config = config;
    this.clock = clock;
  }

  @Override
  public @Nonnull TaskResult execute(@Nonnull StageExecution stage) {
    DryRunContext context = stage.mapTo(DryRunContext.class);

    Instant now = clock.instant();

    if (stage.getStartTime() != null
        && Instant.ofEpochMilli(stage.getStartTime()).plus(context.getWaitTime()).isBefore(now)) {
      return TaskResult.SUCCEEDED;
    } else {
      return TaskResult.RUNNING;
    }
  }

  @Override
  public long getDynamicBackoffPeriod(StageExecution stage, Duration taskDuration) {
    DryRunContext context = stage.mapTo(DryRunContext.class);

    // Return a backoff time that reflects the requested waitTime
    if (stage.getStartTime() != null) {
      Instant now = clock.instant();
      Instant completion = Instant.ofEpochMilli(stage.getStartTime()).plus(context.getWaitTime());

      if (completion.isAfter(now)) {
        return completion.toEpochMilli() - now.toEpochMilli();
      }
    }

    return getBackoffPeriod();
  }

  @Override
  public long getBackoffPeriod() {
    return config.getBackoffPeriod().toMillis();
  }

  @Override
  public long getTimeout() {
    return config.getTimeout().toMillis();
  }
}
