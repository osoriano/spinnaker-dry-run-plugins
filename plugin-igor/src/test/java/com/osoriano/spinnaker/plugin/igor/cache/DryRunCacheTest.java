package com.osoriano.spinnaker.plugin.igor.cache;

import static com.osoriano.spinnaker.plugin.igor.cache.DryRunCache.LAST_PUBLISH_TIMESTAMP;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import redis.clients.jedis.commands.JedisCommands;

public class DryRunCacheTest {
  private RedisClientDelegate redisClientDelegate = mock(RedisClientDelegate.class);
  private IgorConfigurationProperties igorConfigurationProperties =
      new IgorConfigurationProperties();
  private DryRunCache cache = new DryRunCache(redisClientDelegate, igorConfigurationProperties);

  private String artifactName = "artifactName";
  private String timestamp = "12345";
  private String expectedKey = "igor:dryrun:artifactName";

  @Test
  @SuppressWarnings("unchecked")
  public void setCacheValue_SetsExpectedValue() {
    JedisCommands jedisCommandsMock = mock(JedisCommands.class);
    ArgumentCaptor<Consumer<JedisCommands>> captor = ArgumentCaptor.forClass(Consumer.class);

    cache.setCacheValue(artifactName, timestamp);
    verify(redisClientDelegate).withCommandsClient(captor.capture());
    captor.getValue().accept(jedisCommandsMock);

    verify(jedisCommandsMock, times(1)).hset(expectedKey, LAST_PUBLISH_TIMESTAMP, timestamp);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void getLatestBuildIds_ReturnsExpectedValue() {
    JedisCommands jedisCommandsMock = mock(JedisCommands.class);
    when(jedisCommandsMock.hget(expectedKey, LAST_PUBLISH_TIMESTAMP)).thenReturn(timestamp);

    cache.getCacheValue(artifactName);
    ArgumentCaptor<Function<JedisCommands, String>> captor =
        ArgumentCaptor.forClass(Function.class);
    verify(redisClientDelegate).withCommandsClient(captor.capture());

    String result = captor.getValue().apply(jedisCommandsMock);

    assertEquals(result, timestamp);
  }
}
