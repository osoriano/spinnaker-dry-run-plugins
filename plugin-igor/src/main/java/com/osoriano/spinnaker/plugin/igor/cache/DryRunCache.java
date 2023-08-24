package com.osoriano.spinnaker.plugin.igor.cache;

import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Cache used for Dry Run artifacts */
@Service
public class DryRunCache {

  private final RedisClientDelegate redisClientDelegate;
  private final IgorConfigurationProperties igorConfigurationProperties;

  public static final String LAST_PUBLISH_TIMESTAMP = "lastPublishTimestamp";

  @Autowired
  public DryRunCache(
      RedisClientDelegate redisClientDelegate,
      IgorConfigurationProperties igorConfigurationProperties) {
    this.redisClientDelegate = redisClientDelegate;
    this.igorConfigurationProperties = igorConfigurationProperties;
  }

  // Sets the artifact's cache value to the timestamp
  public void setCacheValue(String artifactName, String timestamp) {
    String key = makeKey(artifactName);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key, LAST_PUBLISH_TIMESTAMP, timestamp);
        });
  }

  // Returns timestamp for the given artifact
  public String getCacheValue(String artifactName) {
    return redisClientDelegate.withCommandsClient(
        c -> {
          String ts = c.hget(makeKey(artifactName), LAST_PUBLISH_TIMESTAMP);
          return ts == null ? "0" : ts;
        });
  }

  private String makeKey(String artifactName) {
    return String.join(":", prefix(), "dryrun", artifactName);
  }

  private String prefix() {
    return igorConfigurationProperties.getSpinnaker().getJedis().getPrefix();
  }
}
