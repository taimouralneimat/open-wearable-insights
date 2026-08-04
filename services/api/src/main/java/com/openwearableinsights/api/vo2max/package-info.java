/**
 * Module: vo2max
 *
 * <p>See docs/architecture/component-view.md and ADR-0005 for module strategy.
 *
 * <p>Backs docs/product/parity-matrix.md row 18 ("VO2 Max estimate"). Reads
 * real {@code max_heart_rate} from FIT-parsed activity sessions (see the
 * {@code activities} module) and the account's own resting-heart-rate
 * baseline (see {@code readiness.application.BaselineService}) to compute a
 * non-exercise-regression VO2max estimate. Does not reproduce any vendor's
 * proprietary formula — see {@code vo2max.application.Vo2MaxService}'s
 * class Javadoc for the cited, published methodology.
 */
package com.openwearableinsights.api.vo2max;
