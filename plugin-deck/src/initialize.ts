import { escape } from 'lodash';
import { HelpContentsRegistry } from '@spinnaker/core';

/*
 * `initialize` can be used to hook into arbitrary Deck services.
 * The HelpContentsRegistry provides the help field text for the `DryRunStageConfig`.

 * You can hook into any service exported by the `@spinnaker/core` NPM module, e.g.:
 *  - CloudProviderRegistry
 *  - DeploymentStrategyRegistry

 * When you use a registry, you are diving into Deck's implementation to add functionality.
 * These registries and their methods may change without warning.
*/
export const initialize = () => {
  HelpContentsRegistry.register('osoriano.dryRunStage.waitTime', 'Time (in ISO-8601 format) to wait for the stage to complete');
};
