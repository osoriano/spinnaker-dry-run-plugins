package com.osoriano.spinnaker.plugin.igor.model;

import com.netflix.spinnaker.igor.polling.PollingDelta;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DryRunPollingDelta implements PollingDelta<DryRunDelta> {
  List<DryRunDelta> items;
}
