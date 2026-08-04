/**
 * Module: biomarkers
 *
 * <p>Blood biomarker (lab bloodwork) import — docs/product/parity-matrix.md
 * row 22. Users upload their own lab results as CSV; this module parses,
 * stores, and serves them back grouped/plotted alongside wearable trends.
 * CSV only in v1 — lab PDF formats vary too much to parse reliably without
 * real-world samples, which don't exist in this project.
 *
 * <p>Deliberately a separate table/module from {@code measurements}
 * (continuous wearable telemetry): lab readings carry their own per-reading
 * reference range and lab-style category, and arrive as discrete,
 * infrequent, user-supplied records rather than a device data stream.
 *
 * <p>Honesty rule (see V08 migration and BiomarkerReferenceCatalog): a
 * reference range is only ever shown when it came from the user's own CSV
 * row. Nothing in this module invents a "typical" range or a diagnostic
 * interpretation — every user-facing surface for this data pairs it with an
 * explicit "not medical advice" disclaimer.
 *
 * <p>See docs/architecture/component-view.md and ADR-0005 for module strategy.
 */
package com.openwearableinsights.api.biomarkers;
