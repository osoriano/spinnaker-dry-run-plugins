import React from 'react';

import type { IExecutionDetailsSectionProps } from '@spinnaker/core';
import { ExecutionDetailsSection } from '@spinnaker/core';

/*
 * You can use this component to provide information to users
 * based on the execution response
 *
 * In general, you will access two properties of `props.stage`:
 * - `props.stage.outputs` maps to your SimpleStage's `Output` class.
 * - `props.stage.context` maps to your SimpleStage's `Context` class.
 */
export function DryRunStageExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage, name, current } = props;
  const { context } = stage;
  const { exception } = context;
  return (
    <ExecutionDetailsSection name={name} current={current}>
      {exception && (
        <div>
          <p>
            <b>Exception Type</b> {exception.exceptionType}
          </p>
          <p>
            <b>Error</b> {exception.details.error}
          </p>
          <p>
            <b>Errors</b> {exception.details.errors.join(', ')}
          </p>
          <p>
            <b>See JSON response for more details</b>
          </p>
        </div>
      )}
      {context && (
        <div>
          <p>
            <b>Wait Time</b> {context.waitTime} (ISO-8601 format)
          </p>
        </div>
      )}
    </ExecutionDetailsSection>
  );
}

// The title here will be used as the tab name inside the
// pipeline stage execution view. Camel case will be mapped
// to space-delimited text: dryRun -> DryRun
// TODO: refactor this to not use namespace
//       this is consistent with upstream
// eslint-disable-next-line
export namespace DryRunStageExecutionDetails {
  export const title = 'dryRun';
}
