package com.osoriano.spinnaker.plugin.task

import com.netflix.spinnaker.keel.model.OrcaJob
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class DryRunTasks() {

  /**
   * Create a dry run task that will wait according to the specific time.
   * If fail is true, then the task status will fail after waiting.
   */
  fun createDryRunTask(
    name: String,
    waitTime: Duration,
    fail: Boolean,
  ): List<OrcaJob> {
    val stages = mutableListOf<OrcaJob>()

    if (waitTime != Duration.ZERO) {
      val stageMap = mapOf(
        "name" to name,
        "waitTime" to waitTime.toSeconds(),
      )
      val stage = OrcaJob("wait", stageMap)
      stages.add(stage)
    }

    if (fail) {
      val stageMap = mapOf(
        "name" to "dryrun fail",
        "preconditions" to listOf(
          mapOf(
            "context" to mapOf(
              "expression" to "false",
            ),
            "failPipeline" to true,
            "type" to "expression",
          ),
        ),
      )
      val stage = OrcaJob("checkPreconditions", stageMap)
      stages.add(stage)
    }

    return stages
  }
}
