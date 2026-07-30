/**
 * Named interface: identity application services.
 *
 * <p>Exposed so other modules (e.g. journal, for identity-based habit
 * "votes" tied to the profile's primary goal) may read the profile
 * directly without reaching into internal packages.
 */
@org.springframework.modulith.NamedInterface("application")
package com.openwearableinsights.api.identity.application;
