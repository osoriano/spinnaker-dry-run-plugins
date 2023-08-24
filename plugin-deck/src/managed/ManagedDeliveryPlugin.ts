import type { IConstraintHandler, IManagedDeliveryPlugin, IResourceKindConfig } from '@spinnaker/core';

import { DryRunConstraintHandler } from './DryRunConstraintHandler';
import { DryRunResourceKind } from './DryRunResourceKind';

export class ManagedDeliveryPlugin implements IManagedDeliveryPlugin {
  constraints: IConstraintHandler[] = [new DryRunConstraintHandler()];
  resources: IResourceKindConfig[] = [new DryRunResourceKind()];
}
