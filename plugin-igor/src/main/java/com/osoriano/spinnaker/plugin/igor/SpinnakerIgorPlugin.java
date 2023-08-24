package com.osoriano.spinnaker.plugin.igor;

import com.netflix.spinnaker.kork.plugins.api.spring.SpringLoaderPlugin;
import java.util.List;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpinnakerIgorPlugin extends SpringLoaderPlugin {
  private static final Logger log = LoggerFactory.getLogger(SpinnakerIgorPlugin.class);

  public SpinnakerIgorPlugin(PluginWrapper wrapper) {
    super(wrapper);
  }

  @Override
  public List<String> getPackagesToScan() {
    return List.of("com.osoriano.spinnaker.plugin.igor");
  }

  @Override
  public void start() {
    log.info("SpinnakerIgorPlugin.start()");
  }

  @Override
  public void stop() {
    log.info("SpinnakerIgorPlugin.stop()");
  }
}
