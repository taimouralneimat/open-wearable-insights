package com.openwearableinsights.api.biomarkers.domain;

/**
 * A single persisted blood biomarker reading, as returned to API clients.
 *
 * @param referenceLow  low bound of the reference range, or {@code null} —
 *                       always taken verbatim from the user's own imported
 *                       CSV row, never synthesized (see V08 migration)
 * @param referenceHigh high bound of the reference range, or {@code null}
 * @param inRange        {@code true}/{@code false} when a reference range
 *                       was supplied (either bound), computed by simple
 *                       comparison against the user's own numbers —
 *                       {@code null} when no range was supplied, so the UI
 *                       can show an honest "no reference range provided"
 *                       state instead of a fabricated verdict
 * @param readingDate    ISO-8601 {@code yyyy-MM-dd}
 */
public record BiomarkerReading(
        Long id,
        String biomarkerName,
        String category,
        double value,
        String unit,
        Double referenceLow,
        Double referenceHigh,
        Boolean inRange,
        String readingDate
) {

    /**
     * Computes {@link #inRange} from a value and optional reference bounds.
     * Either bound alone is enough (e.g. many labs only publish an upper
     * bound, like triglycerides' "below X"). Returns {@code null} when
     * neither bound is present.
     */
    public static Boolean computeInRange(double value, Double referenceLow, Double referenceHigh) {
        if (referenceLow == null && referenceHigh == null) {
            return null;
        }
        boolean aboveLow = referenceLow == null || value >= referenceLow;
        boolean belowHigh = referenceHigh == null || value <= referenceHigh;
        return aboveLow && belowHigh;
    }
}
