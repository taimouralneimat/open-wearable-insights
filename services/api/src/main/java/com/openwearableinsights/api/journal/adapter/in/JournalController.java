package com.openwearableinsights.api.journal.adapter.in;

import com.openwearableinsights.api.identity.application.ProfileService;
import com.openwearableinsights.api.journal.application.CorrelationService;
import com.openwearableinsights.api.journal.application.JournalService;
import com.openwearableinsights.api.journal.domain.BehaviorCategory;
import com.openwearableinsights.api.journal.domain.BehaviorCorrelation;
import com.openwearableinsights.api.journal.domain.HabitStreak;
import com.openwearableinsights.api.journal.domain.IdentityVotes;
import com.openwearableinsights.api.journal.domain.JournalEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for the behavioral journal.
 *
 * <p>Entries are self-reported and always treated as untrusted input —
 * they're stored with provenance, never used to override a computed
 * score. See docs/product/parity-matrix.md row 5/6.
 */
@RestController
@RequestMapping("/api/v1/journal")
@Tag(name = "Journal", description = "Behavioral journal — self-reported, always treated as untrusted input")
public class JournalController {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    private static final int IDENTITY_VOTES_WINDOW_DAYS = 30;

    private final JournalService journalService;
    private final CorrelationService correlationService;
    private final ProfileService profileService;

    public JournalController(JournalService journalService, CorrelationService correlationService, ProfileService profileService) {
        this.journalService = journalService;
        this.correlationService = correlationService;
        this.profileService = profileService;
    }

    @GetMapping("/behaviors")
    @Operation(summary = "Get the curated behavior taxonomy",
            description = "Suggested behaviors grouped by category, for the UI to offer. Not an enforced constraint — custom free-text behaviors are also accepted.")
    public List<BehaviorCategory> getBehaviors() {
        return journalService.getTaxonomy();
    }

    @PostMapping("/entries")
    @Operation(summary = "Log a journal entry",
            description = "Records a self-reported behavior/observation. Always stored as untrusted input.")
    public JournalEntry addEntry(@Valid @RequestBody JournalEntryRequest request) {
        return journalService.addEntry(
                DEFAULT_ACCOUNT_ID, request.category(), request.behavior(),
                request.value(), request.note()
        );
    }

    @GetMapping("/entries")
    @Operation(summary = "List journal entries",
            description = "Returns the account's journal entries, most recent first. Optionally filtered to entries since a given date.")
    public List<JournalEntry> listEntries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since
    ) {
        return journalService.listEntries(DEFAULT_ACCOUNT_ID, since);
    }

    @GetMapping("/streaks")
    @Operation(summary = "Get current and longest streaks per logged behavior",
            description = "Consecutive-day streaks for every behavior the account has ever logged, sorted by current streak descending. currentStreak is 0 once a day is missed, even if longestStreak was higher before.")
    public List<HabitStreak> getStreaks() {
        return journalService.getStreaks(DEFAULT_ACCOUNT_ID);
    }

    @GetMapping("/identity-votes")
    @Operation(summary = "Get identity-based habit \"votes\" toward the profile's primary goal",
            description = "Atomic Habits identity framing: counts logged entries in the last 30 days whose category is relevant to the account's stated primary goal (Settings -> Profile). goal is null with zero votes if no goal is set — never guesses one.")
    public IdentityVotes getIdentityVotes() {
        String goal = profileService.fetchProfile(DEFAULT_ACCOUNT_ID).primaryGoal();
        List<JournalEntry> entries = journalService.listEntries(DEFAULT_ACCOUNT_ID, null);
        return journalService.computeIdentityVotes(goal, entries, IDENTITY_VOTES_WINDOW_DAYS);
    }

    @GetMapping("/correlations")
    @Operation(summary = "Get exploratory behavior correlations",
            description = "Compares readiness scores on days each logged behavior was present vs. absent. Requires a minimum sample size in both groups — behaviors without enough data simply aren't included, not shown with fabricated confidence. Always correlation, never causation.")
    public List<BehaviorCorrelation> getCorrelations() {
        return correlationService.computeCorrelations(DEFAULT_ACCOUNT_ID);
    }

    @GetMapping("/correlations/garmin-signals")
    @Operation(summary = "Get exploratory correlations between behaviors and Garmin-exclusive signals",
            description = "Same methodology as /correlations, but against Body Battery and Garmin's own Training Readiness score instead of this app's readiness score. Only returns results once a Garmin Connect sync has provided that data.")
    public List<com.openwearableinsights.api.journal.domain.GarminSignalCorrelation> getGarminSignalCorrelations() {
        return correlationService.computeGarminSignalCorrelations(DEFAULT_ACCOUNT_ID);
    }

    public record JournalEntryRequest(
            String category,
            @NotBlank String behavior,
            String value,
            String note
    ) {}
}
