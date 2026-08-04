package com.openwearableinsights.api.biomarkers.domain;

/**
 * One entry in the static, shipped-with-the-app reference catalog of known
 * biomarker names — a label/category/description lookup for display only.
 *
 * <p>Deliberately carries no numeric reference range. This app never shows a
 * "normal range" it didn't get from the user's own lab CSV — see
 * {@link com.openwearableinsights.api.biomarkers.application.BiomarkerReferenceCatalog}
 * for why.
 */
public record BiomarkerReferenceEntry(
        String name,
        String category,
        String description
) {}
