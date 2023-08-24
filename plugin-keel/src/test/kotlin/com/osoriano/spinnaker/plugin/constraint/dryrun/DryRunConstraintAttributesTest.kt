package com.osoriano.spinnaker.plugin.constraint.dryrun

import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.Duration

class DryRunConstraintAttributesTest {

  @Test
  fun testContraintAttributes() {
    val attributes = DryRunConstraintAttributes(
      waitTime = Duration.ofSeconds(30),
      fail = false,
      alternate = true,
      alternateInterval = Duration.ofSeconds(60),
    )
    assertEquals(attributes.waitTime.toSeconds(), 30)
    assertEquals(attributes.fail, false)
    assertEquals(attributes.alternate, true)
    assertEquals(attributes.alternateInterval.toSeconds(), 60)
  }
}
