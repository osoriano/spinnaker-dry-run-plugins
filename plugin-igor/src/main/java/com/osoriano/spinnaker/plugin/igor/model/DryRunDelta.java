package com.osoriano.spinnaker.plugin.igor.model;

import com.netflix.spinnaker.igor.polling.DeltaItem;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DryRunDelta implements DeltaItem {
  String artifactName;
}
