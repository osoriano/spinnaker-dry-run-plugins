package com.osoriano.spinnaker.plugin.stage.dryrun;

import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Context is used within the stage itself and returned to the Orca pipeline execution. */
@AllArgsConstructor
@NoArgsConstructor
@Data
class DryRunContext {
  private Duration waitTime;
}
