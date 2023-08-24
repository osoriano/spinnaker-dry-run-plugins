package com.osoriano.spinnaker.plugin.stage.dryrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class DryRunTaskTest {

  Clock clock;
  StageExecution stage;
  DryRunTask task;

  /* Create two points in time. t2 is the time after the waitTime has elapsed */
  Duration waitTime = Duration.ofSeconds(3);
  Instant t1 = Instant.parse("2023-01-25T12:00:00.00Z");
  Instant t2 = t1.plus(waitTime).plusSeconds(1L);

  @Before
  public void setup() {
    /* Plugin config */
    Duration backoffPeriod = Duration.ofSeconds(1);
    Duration timeout = Duration.ofSeconds(2);
    DryRunPluginConfig pluginConfig = new DryRunPluginConfig(backoffPeriod, timeout);

    /* Stage config */
    Map<String, Object> context = Map.of("waitTime", waitTime.toString());

    /* Stage mock */
    stage = new StageExecutionImpl();
    stage.setContext(context);

    /* Clock mock */
    clock = mock(Clock.class);

    /* Create task to test */
    task = new DryRunTask(pluginConfig, clock);
  }

  @Test
  public void testGetBackoffPeriod() {
    assertEquals(Duration.ofSeconds(1).toMillis(), task.getBackoffPeriod());
  }

  @Test
  public void testGetTimeout() {
    assertEquals(Duration.ofSeconds(2).toMillis(), task.getTimeout());
  }

  @Test
  public void testGetDynamicBackoffPeriod() {
    // If stage hasn't started, fall back to default backoff
    stage.setStartTime(null);
    assertEquals(
        Duration.ofSeconds(1).toMillis(), task.getDynamicBackoffPeriod(stage, null /* unused */));

    // If stage has started, backoff is set to to the remaining wait time
    stage.setStartTime(t1.toEpochMilli());
    when(clock.instant()).thenReturn(t1);
    assertEquals(
        Duration.ofSeconds(3).toMillis(), task.getDynamicBackoffPeriod(stage, null /* unused */));

    // If wait time has elapsed, fall back to default backoff
    stage.setStartTime(t1.toEpochMilli());
    when(clock.instant()).thenReturn(t2);
    assertEquals(
        Duration.ofSeconds(1).toMillis(), task.getDynamicBackoffPeriod(stage, null /* unused */));
  }

  @Test
  public void testExecuteTask() {
    // If stage is not started, consider task as running
    stage.setStartTime(null);
    assertEquals(ExecutionStatus.RUNNING, task.execute(stage).getStatus());

    // If wait time has not elapsed, consider task as running
    stage.setStartTime(t1.toEpochMilli());
    when(clock.instant()).thenReturn(t1);
    assertEquals(ExecutionStatus.RUNNING, task.execute(stage).getStatus());

    // If wait time has elapsed, consider task as succcessful
    stage.setStartTime(t1.toEpochMilli());
    when(clock.instant()).thenReturn(t2);
    assertEquals(ExecutionStatus.SUCCEEDED, task.execute(stage).getStatus());
  }
}
