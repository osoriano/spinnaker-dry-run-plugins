package com.osoriano.spinnaker.plugin.resource.exceptions

import com.netflix.spinnaker.keel.persistence.NoSuchEntityException

/**
 * Subclass of NoSuchEntityException which is used when an environment for a given resource is not found
 */
class EnvironmentNotFoundException(reference: String, deliveryConfig: String?) :
  NoSuchEntityException("No environment with resource id: $reference is found in delivery config environments $deliveryConfig")
