package com.osoriano.spinnaker.plugin;

import com.netflix.spinnaker.kork.plugins.api.spring.SpringLoaderPlugin;
import java.util.List;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpinnakerOrcaPlugin extends SpringLoaderPlugin {
  private static final Logger log = LoggerFactory.getLogger(SpinnakerOrcaPlugin.class);

  public SpinnakerOrcaPlugin(PluginWrapper wrapper) {
    super(wrapper);
  }

  @Override
  public List<String> getPackagesToScan() {
    return List.of("com.osoriano.spinnaker.plugin.stage.dryrun");
  }

  @Override
  public void start() {
    log.info("SpinnakerOrcaPlugin.start()");
  }

  @Override
  public void stop() {
    log.info("SpinnakerOrcaPlugin.stop()");
  }
}
