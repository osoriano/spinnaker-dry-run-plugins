package com.osoriano.spinnaker.plugin.resource.exceptions

import com.netflix.spinnaker.keel.core.ResourceCurrentlyUnresolvable

/**
 * During resource desired state resolution, artifacts may be waiting to be approved.
 *
 * <p>Throw a subclass of ResourceCurrentlyUnresolvable, which is treated as a non-fatal or transient
 * exception
 */
class NoVersionAvailable(name: String, type: String) : ResourceCurrentlyUnresolvable("No version available for deployment with name, $name, and type, $type")
