package com.openwearableinsights.api.vo2max.adapter.in;

import com.openwearableinsights.api.vo2max.application.Vo2MaxService;
import com.openwearableinsights.api.vo2max.domain.Vo2MaxEstimate;
import com.openwearableinsights.api.vo2max.domain.Vo2MaxTrendPoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the VO2max (maximal oxygen uptake) estimate.
 *
 * <p>Backs docs/product/parity-matrix.md row 18 ("VO2 Max estimate") — see
 * {@link Vo2MaxService} for the cited, published, non-proprietary
 * methodology and exactly which real data sources feed it.
 */
@RestController
@RequestMapping("/api/v1/vo2max")
@Tag(name = "VO2 Max", description = "VO2max (maximal oxygen uptake) estimate and trend")
public class Vo2MaxController {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    private final Vo2MaxService vo2MaxService;

    public Vo2MaxController(Vo2MaxService vo2MaxService) {
        this.vo2MaxService = vo2MaxService;
    }

    @GetMapping
    @Operation(summary = "Get the current VO2max estimate",
            description = "Estimates VO2max from the account's own highest recorded activity heart rate and "
                    + "personal resting-heart-rate baseline, using the published Uth-Sorensen-Overgaard-Pedersen "
                    + "non-exercise regression (Heart Rate Ratio Method, 2004). Not a lab-measured or medical "
                    + "VO2max — see the response's methodology and limitations fields. Reports an honest empty "
                    + "estimate (confidence 'none') when there isn't enough real activity or resting-heart-rate "
                    + "data yet.")
    public Vo2MaxEstimate getEstimate() {
        return vo2MaxService.computeEstimate(DEFAULT_ACCOUNT_ID);
    }

    @GetMapping("/trend")
    @Operation(summary = "Get the monthly VO2max trend",
            description = "Returns up to the last 12 calendar months of VO2max estimates, each computed "
                    + "independently from that month's own real activity max-heart-rate and average resting "
                    + "heart rate. Months without enough real data for both inputs are omitted, not zero-filled.")
    public List<Vo2MaxTrendPoint> getTrend() {
        return vo2MaxService.computeTrend(DEFAULT_ACCOUNT_ID);
    }
}
