/**
 * Module: healthspan
 *
 * <p>See docs/architecture/component-view.md and ADR-0005 for module strategy.
 *
 * <p>Backs docs/product/parity-matrix.md row 24 ("Healthspan / biological
 * age score"). Composes real, already-computed outputs from other modules
 * (VO2max, Strength Activity Time, resting-heart-rate baselines, sleep
 * consistency, training load) into this app's own original composite
 * wellness score — never a reproduction of any vendor's proprietary
 * biological-age formula, and never developed with or validated by any
 * external research institute. See {@code
 * healthspan.application.HealthspanService}'s class Javadoc for the full,
 * disclosed methodology.
 */
package com.openwearableinsights.api.healthspan;
