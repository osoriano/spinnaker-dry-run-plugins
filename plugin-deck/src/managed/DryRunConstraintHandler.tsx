import React from 'react';

import type { IBaseConstraint, IConstraintHandler } from '@spinnaker/core';
import { IconNames } from '@spinnaker/presentation';

const DryRunDescription = ({ constraint }: { constraint: IBaseConstraint }) => {
  return <div>Sample description for constraint</div>;
};

export class DryRunConstraintHandler implements IConstraintHandler {
  kind = 'osoriano/dry-run-constraint@v1';
  iconName = {
    BLOCKED: 'mdError',
    NOT_EVALUATED: 'checkboxIndeterminate',
    PENDING: 'spMenuTimeline',
    FAIL: 'mdConstraintGeneric',
    PASS: 'checkBadge',
    FORCE_PASS: 'caretRight',
    OVERRIDE_PASS: 'manualJudgementApproved',
    OVERRIDE_FAIL: 'manualJudgementRejected',
    DEFAULT: 'mdUnknown',
  };
  displayTitle = {
    displayName: 'Dry Run Constraint',
    displayStatus: this.getDisplayStatus,
  };
  descriptionRender = DryRunDescription;
  overrideActions = {
    FAIL: [
      {
        title: 'Skip dry run constraint',
        pass: true,
      },
    ],
    PENDING: [
      {
        title: 'Override Fail',
        pass: false,
      },
      {
        title: 'Override Pass',
        pass: true,
      },
    ],
  };

  public getDisplayStatus({ constraint }: { constraint: IBaseConstraint }): string {
    switch (constraint.status) {
      case 'BLOCKED':
        return 'Dry run blocked';
      case 'NOT_EVALUATED':
        return 'Dry run not evaluated';
      case 'PENDING':
        return 'Dry run pending';
      case 'FAIL':
        return 'Dry run failed';
      case 'PASS':
        return 'Dry run passed';
      case 'FORCE_PASS':
        return 'forced by user (pass)';
      case 'OVERRIDE_PASS':
        return 'overridden by user (pass)';
      case 'OVERRIDE_FAIL':
        return 'overridden by user (fail)';
      default:
        return `unknown status: ${constraint.status}`;
    }
  }
}
