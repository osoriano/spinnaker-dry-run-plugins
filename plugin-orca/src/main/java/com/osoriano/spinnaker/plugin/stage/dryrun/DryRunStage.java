package com.osoriano.spinnaker.plugin.stage.dryrun;

import com.netflix.spinnaker.kork.plugins.api.spring.ExposeToApp;
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import org.springframework.stereotype.Component;

/**
 * By implementing StageDefinitionBuilder, your stage is available for use in Spinnaker.
 *
 * @see com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
 */
@Component
@ExposeToApp
public class DryRunStage implements StageDefinitionBuilder {

  /** This function describes the sequence of substeps, or "tasks" that comprise this stage */
  @Override
  public void taskGraph(StageExecution stage, TaskNode.Builder builder) {
    builder.withTask("dryRunTask", DryRunTask.class);
  }
}
