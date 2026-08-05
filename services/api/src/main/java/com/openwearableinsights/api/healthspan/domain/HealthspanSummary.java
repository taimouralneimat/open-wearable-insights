package com.openwearableinsights.api.healthspan.domain;

/**
 * The full healthspan response: both windows the acceptance criteria calls
 * for (docs/product/parity-matrix.md row 24), computed by the exact same
 * methodology — only the "recent vs. prior" comparison window differs. See
 * {@code HealthspanService#computeSummary}.
 *
 * @param thirtyDay a shorter-horizon read on recent trajectory
 * @param sixMonth  a longer-horizon read — more resistant to short-term
 *                  noise, closer in spirit to the "pace of aging" framing
 *                  this feature is inspired by
 */
public record HealthspanSummary(
        HealthspanScore thirtyDay,
        HealthspanScore sixMonth
) {}
