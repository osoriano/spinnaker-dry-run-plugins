import React from 'react';
import type { IStage, IStageConfigProps, IStageTypeConfig } from '@spinnaker/core';
import {
  ExecutionDetailsSection,
  ExecutionDetailsTasks,
  FormikFormField,
  FormikStageConfig,
  FormValidator,
  HelpContentsRegistry,
  HelpField,
  IExecutionDetailsSectionProps,
  TextInput,
  Validators,
} from '@spinnaker/core';

import './DryRunStage.less';

/*
 * Renders the stage configuration form.
 *
 * IStageConfigProps defines properties passed to all Spinnaker Stages.
 */
export function DryRunStageConfig(props: IStageConfigProps) {

  return (
    <div className="DryRunStageConfig">
      <FormikStageConfig
        {...props}
        validate={validate}
        onChange={props.updateStage}
        render={(props) => (
          <>
            <FormikFormField
              name="waitTime"
              label="Wait Time"
              help={<HelpField id="osoriano.dryRunStage.waitTime" />}
              input={(props) => <TextInput {...props} />}
            />
          </>
        )}
      />
    </div>
  );
}

export function validate(stageConfig: IStage) {
  const validator = new FormValidator(stageConfig);

  validator.field('waitTime').required();

  return validator.validateForm();
}
