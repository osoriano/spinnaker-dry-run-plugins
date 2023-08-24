import type { IDeckPlugin } from '@spinnaker/core';

import { initialize } from './initialize';
import { ManagedDeliveryPlugin } from './managed/ManagedDeliveryPlugin';
import { dryRunStage } from './stage/dryrun/DryRunStage';

export const plugin: IDeckPlugin = {
  initialize,
  stages: [dryRunStage],
  managedDelivery: new ManagedDeliveryPlugin(),
};
