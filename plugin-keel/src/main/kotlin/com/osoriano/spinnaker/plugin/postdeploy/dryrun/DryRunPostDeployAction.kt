package com.osoriano.spinnaker.plugin.postdeploy.dryrun

import com.netflix.spinnaker.keel.api.postdeploy.PostDeployAction
import java.time.Duration

const val DRY_RUN_POST_DEPLOY_V1 = "osoriano/dry-run-post-deploy@v1"

/** A dry run post deploy action, as defined in a DeliveryConfig. */
data class DryRunPostDeployAction(
  val waitTime: Duration,
  val fail: Boolean,
) : PostDeployAction() {

  override val type = DRY_RUN_POST_DEPLOY_V1
  override val id = DRY_RUN_POST_DEPLOY_V1
}
