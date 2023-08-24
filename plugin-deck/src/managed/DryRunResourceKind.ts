import type { IResourceKindConfig } from '@spinnaker/core';
import type { IManagedResourceSummary } from '@spinnaker/core/lib/domain';
import type { IconNames } from '@spinnaker/presentation';

export class DryRunResourceKind implements IResourceKindConfig {
  kind = 'osoriano/dry-run-resource@v1';
  iconName: IconNames = 'spMenuClusters';
  experimentalDisplayLink = this.displayLink();

  public displayLink(): (resource: IManagedResourceSummary) => string {
    return function (resource: IManagedResourceSummary) {
      // Use a mock link since this is a dry run resource
      return 'https://github.com/osoriano/spinnaker-dry-run-plugins';
    };
  }
}
