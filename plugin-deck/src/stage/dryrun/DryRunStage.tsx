import React from 'react';

import type { IStageTypeConfig } from '@spinnaker/core';
import { ExecutionDetailsTasks } from '@spinnaker/core';

import { DryRunStageConfig, validate } from './DryRunStageConfig';
import { DryRunStageExecutionDetails } from './DryRunStageExecutionDetails';

/*
  Define Spinnaker Stages with IStageTypeConfig.
  Required options: https://github.com/spinnaker/deck/master/app/scripts/modules/core/src/domain/IStageTypeConfig.ts
  - label -> The name of the Stage
  - description -> Long form that describes what the Stage actually does
  - key -> A unique name for the Stage in the UI; ties to Orca backend
  - component -> The rendered React component
  - validateFn -> A validation function for the stage config form.
 */
export const dryRunStage: IStageTypeConfig = {
  key: 'dryRun',
  label: 'Dry Run Stage',
  description: 'Dry run stage that waits for a specified time',
  component: DryRunStageConfig,
  executionDetailsSections: [DryRunStageExecutionDetails, ExecutionDetailsTasks],
  validateFn: validate,
  restartable: true,
  strategy: true,
};
