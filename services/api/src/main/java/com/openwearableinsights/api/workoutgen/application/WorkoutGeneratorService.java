package com.openwearableinsights.api.workoutgen.application;

import com.openwearableinsights.api.trainingload.application.TrainingLoadService;
import com.openwearableinsights.api.trainingload.domain.TrainingLoadSummary;
import com.openwearableinsights.api.workoutgen.application.WorkoutExerciseLibrary.ExerciseDefinition;
import com.openwearableinsights.api.workoutgen.domain.Equipment;
import com.openwearableinsights.api.workoutgen.domain.ExerciseCategory;
import com.openwearableinsights.api.workoutgen.domain.GeneratedWorkout;
import com.openwearableinsights.api.workoutgen.domain.Goal;
import com.openwearableinsights.api.workoutgen.domain.Limitation;
import com.openwearableinsights.api.workoutgen.domain.WorkoutExercise;
import com.openwearableinsights.api.workoutgen.domain.WorkoutGenerationRequest;
import com.openwearableinsights.api.workoutgen.domain.WorkoutSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic, template-based workout generator — backs docs/product/
 * parity-matrix.md row 26 ("AI workout generation").
 *
 * <p><b>What this deliberately is not:</b> competitor public docs describe an
 * LLM-generated custom workout. This service does not call an LLM at all —
 * see the {@code workoutgen} module's package-info "LLM path" section for
 * why that's deliberately deferred rather than shipped unverified. This is a
 * real rule-based generator against a curated, versioned exercise library
 * ({@link WorkoutExerciseLibrary}), not a disguised random picker: the exact
 * same request always produces the exact same workout (see "Determinism"
 * below), and every output traces back to an explicit filter/selection rule
 * a person can read in this class.
 *
 * <p><b>Algorithm ({@value #ALGORITHM_VERSION}):</b>
 * <ol>
 *   <li><b>Normalize inputs</b> — a missing goal defaults to {@link
 *   Goal#GENERAL_FITNESS}; missing/empty equipment means bodyweight-only
 *   (bodyweight is always implicitly available regardless of what's
 *   selected — see {@link #isAvailable}); missing limitations means none
 *   stated; duration is clamped to [{@value #MIN_DURATION_MINUTES}, {@value
 *   #MAX_DURATION_MINUTES}] minutes, defaulting to {@value
 *   #DEFAULT_DURATION_MINUTES} when absent.</li>
 *   <li><b>Pick a per-goal intensity template</b> (sets / reps-or-duration /
 *   rest) for the main block — see {@link #mainProfileFor}. Warmup and
 *   cooldown always use their own fixed, lighter templates.</li>
 *   <li><b>Size the main block</b> from the requested duration: {@value
 *   #MINUTES_PER_WARMUP_EXERCISE} min/warmup-exercise and {@value
 *   #MINUTES_PER_COOLDOWN_EXERCISE} min/cooldown-exercise (fixed counts,
 *   {@value #WARMUP_COUNT} and {@value #COOLDOWN_COUNT}) are subtracted from
 *   the requested duration; the remaining budget is divided by {@value
 *   #MINUTES_PER_MAIN_EXERCISE} minutes/main-exercise (a documented v1
 *   simplifying constant — same "simplest defensible estimate, openly
 *   stated" tradeoff {@code TrainingLoadService#FALLBACK_ZONE_WEIGHT} and
 *   {@code StrengthTrainingService#ESTIMATED_MINUTES_PER_SET} already make
 *   elsewhere in this app) and clamped to [{@value #MIN_MAIN_EXERCISES},
 *   {@value #MAX_MAIN_EXERCISES}].</li>
 *   <li><b>Filter the library</b> per block to exercises tagged with the
 *   normalized goal (warmup/cooldown entries are tagged with every goal —
 *   see {@link WorkoutExerciseLibrary}), available given the normalized
 *   equipment, and with no contraindication overlapping the normalized
 *   limitations.</li>
 *   <li><b>Select, in fixed library order</b> (never shuffled, never
 *   random) — the first N matching candidates per block. If fewer than N
 *   are available after filtering, this returns however many real matches
 *   exist and says so honestly in {@link GeneratedWorkout#limitations()}
 *   rather than repeating an exercise to pad the count or fabricating one
 *   outside the library.</li>
 * </ol>
 *
 * <p><b>Determinism:</b> the same {@link WorkoutGenerationRequest} (same
 * goal/equipment/limitations/duration, and — when {@code accountId} is set —
 * the same underlying training-load data) always produces the exact same
 * {@link GeneratedWorkout}. No randomness anywhere in this class.
 *
 * <p><b>Training-load awareness (optional, only when {@code accountId} is
 * present):</b> reuses {@link TrainingLoadService#computeSummary}'s real
 * ACWR-based {@code loadStatus} — the same real figure {@code
 * HealthspanService} and the Sleep Planner (parity-matrix row 20) already
 * compose — rather than a new load formula. Only {@code "high"} (the
 * documented overtraining-risk band, see {@code TrainingLoadService}'s class
 * Javadoc) actually lightens the plan: the main block's exercise count and
 * set count are each reduced by one (floored at {@value
 * #MIN_MAIN_EXERCISES}/{@value #MIN_SETS_FLOOR}), and {@link
 * GeneratedWorkout#intensityAdjustment()} states what changed and why,
 * citing the real ACWR figure. {@code "elevated"} is a weaker, more
 * ambiguous signal (see {@code TrainingLoadService}'s own documented
 * thresholds) — deliberately left as a disclosed caveat rather than a plan
 * change, so a borderline signal doesn't silently override what the user
 * explicitly asked for. Any other status (including {@code "unknown"} or a
 * thrown exception — no training-load data yet) makes no adjustment at all
 * and reports {@code intensityAdjustment = null}, never a fabricated one.
 *
 * <p><b>Deliberately not composed for v1:</b> the account's own logged
 * strength-workout history ({@code strength.application.
 * StrengthTrainingService} / {@code StrengthWorkoutRepository}) is not read
 * to bias exercise selection (e.g. "don't repeat last session's movements"
 * or progressive overload from previously logged weights). Two real
 * reasons: (1) {@code StrengthExercise#exerciseName} is free text, while
 * this library's exercise names are a curated, fixed vocabulary — matching
 * one against the other would be an unreliable fuzzy-string problem, not a
 * real join; (2) progressive-overload logic (recommending heavier
 * weight/more reps than last time) doesn't exist anywhere in this codebase
 * yet and is real, separate scope, not a one-line addition here. The row 26
 * acceptance criteria asks for "generate a custom workout from stated
 * goals/equipment/limitations" — not history-aware progression — so this is
 * judged unnecessary scope for a first version, not an oversight.
 */
@Service
public class WorkoutGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(WorkoutGeneratorService.class);

    /** Versioned, deterministic — see class Javadoc for the full algorithm. */
    public static final String ALGORITHM_VERSION = "workoutgen-v1";

    /**
     * Always present, verbatim, per docs/product/parity-matrix.md row 26's
     * acceptance criteria ("clearly labeled as such, not a prescription").
     * Same "always-present, real API field, not just documentation"
     * convention as {@code HealthspanService#DISCLAIMER}.
     */
    public static final String DISCLAIMER =
            "This is a computer-generated workout suggestion, not a prescription. It was produced by this app's "
                    + "own deterministic, rule-based template engine (not an AI/LLM) matching your stated goal, "
                    + "equipment, and limitations against a fixed, curated exercise library - it is not "
                    + "personalized coaching, physical therapy, or medical advice. Stop any exercise that causes "
                    + "pain, and consult a qualified professional for injuries or medical conditions.";

    private static final int DEFAULT_DURATION_MINUTES = 30;
    private static final int MIN_DURATION_MINUTES = 10;
    private static final int MAX_DURATION_MINUTES = 90;

    private static final int WARMUP_COUNT = 2;
    private static final int COOLDOWN_COUNT = 2;
    private static final int MIN_MAIN_EXERCISES = 2;
    private static final int MAX_MAIN_EXERCISES = 6;
    private static final int MIN_MAIN_BUDGET_MINUTES = 10;

    // Fixed per-exercise minute estimates used both to size the main block
    // from the requested duration and to report durationMinutesEstimated —
    // a documented v1 simplifying constant, not a measurement. See class
    // Javadoc.
    private static final int MINUTES_PER_WARMUP_EXERCISE = 2;
    private static final int MINUTES_PER_MAIN_EXERCISE = 6;
    private static final int MINUTES_PER_COOLDOWN_EXERCISE = 2;

    private static final int MIN_SETS_FLOOR = 2;

    private static final String HIGH_LOAD_STATUS = "high";

    private static final List<String> BASE_LIMITATIONS = List.of(
            "Curated exercise template library (" + WorkoutExerciseLibrary.LIBRARY_VERSION + "), not exhaustive - "
                    + "exercises are selected deterministically from a fixed list, this is not a personal trainer "
                    + "and does not adapt in real time to how an exercise actually feels.",
            "Limitation filtering excludes exercises this app's own curated tagging flags as contraindicated for "
                    + "the selected limitation(s) - not a medical or physical-therapy assessment, and not a "
                    + "guarantee of safety for any specific injury.",
            "Sets/reps/rest are fixed templates per goal, not personalized to your strength level, one-rep max, "
                    + "or prior performance."
    );

    private final TrainingLoadService trainingLoadService;

    public WorkoutGeneratorService(TrainingLoadService trainingLoadService) {
        this.trainingLoadService = trainingLoadService;
    }

    public GeneratedWorkout generate(WorkoutGenerationRequest request) {
        Goal goal = request.goal() != null ? request.goal() : Goal.GENERAL_FITNESS;
        Set<Equipment> equipment = request.equipment() != null && !request.equipment().isEmpty()
                ? EnumSet.copyOf(request.equipment())
                : EnumSet.noneOf(Equipment.class);
        Set<Limitation> limitations = request.limitations() != null && !request.limitations().isEmpty()
                ? EnumSet.copyOf(request.limitations())
                : EnumSet.noneOf(Limitation.class);
        int durationMinutes = clamp(
                request.durationMinutes() != null ? request.durationMinutes() : DEFAULT_DURATION_MINUTES,
                MIN_DURATION_MINUTES, MAX_DURATION_MINUTES);

        IntensityProfile mainProfile = mainProfileFor(goal);
        int mainCount = computeMainCount(durationMinutes);

        String intensityAdjustment = null;
        LoadAdjustment adjustment = request.accountId() != null
                ? computeLoadAdjustment(request.accountId())
                : LoadAdjustment.none();
        if (adjustment.apply()) {
            mainCount = Math.max(mainCount - 1, MIN_MAIN_EXERCISES);
            mainProfile = new IntensityProfile(
                    Math.max(mainProfile.sets() - 1, MIN_SETS_FLOOR),
                    mainProfile.repsOrDuration(),
                    mainProfile.restSeconds());
            intensityAdjustment = String.format(
                    "Your recent training load is 'high' (ACWR %.2f) - a real overtraining-risk signal from your "
                            + "own activity data - so this plan was lightened: one fewer main exercise and one "
                            + "fewer set per exercise than the standard %s template.",
                    adjustment.acwr(), goalLabel(goal));
        }

        List<WorkoutExercise> warmup = selectCandidates(
                ExerciseCategory.WARMUP, goal, equipment, limitations, WARMUP_COUNT, WARMUP_PROFILE);
        List<WorkoutExercise> main = selectCandidates(
                ExerciseCategory.MAIN, goal, equipment, limitations, mainCount, mainProfile);
        List<WorkoutExercise> cooldown = selectCandidates(
                ExerciseCategory.COOLDOWN, goal, equipment, limitations, COOLDOWN_COUNT, COOLDOWN_PROFILE);

        int estimatedMinutes = warmup.size() * MINUTES_PER_WARMUP_EXERCISE
                + main.size() * MINUTES_PER_MAIN_EXERCISE
                + cooldown.size() * MINUTES_PER_COOLDOWN_EXERCISE;

        List<String> limitationsOut = new ArrayList<>(BASE_LIMITATIONS);
        noteShortfall(limitationsOut, "warmup", WARMUP_COUNT, warmup.size());
        noteShortfall(limitationsOut, "main", mainCount, main.size());
        noteShortfall(limitationsOut, "cooldown", COOLDOWN_COUNT, cooldown.size());

        return new GeneratedWorkout(
                ALGORITHM_VERSION,
                WorkoutSource.DETERMINISTIC_TEMPLATE,
                goal,
                Collections.unmodifiableSet(equipment),
                Collections.unmodifiableSet(limitations),
                durationMinutes,
                estimatedMinutes,
                warmup,
                main,
                cooldown,
                intensityAdjustment,
                DISCLAIMER,
                limitationsOut,
                Instant.now()
        );
    }

    private int computeMainCount(int durationMinutes) {
        int fixedBlocksMinutes = WARMUP_COUNT * MINUTES_PER_WARMUP_EXERCISE + COOLDOWN_COUNT * MINUTES_PER_COOLDOWN_EXERCISE;
        int mainBudget = Math.max(durationMinutes - fixedBlocksMinutes, MIN_MAIN_BUDGET_MINUTES);
        int count = (int) Math.round(mainBudget / (double) MINUTES_PER_MAIN_EXERCISE);
        return clamp(count, MIN_MAIN_EXERCISES, MAX_MAIN_EXERCISES);
    }

    /**
     * Real recent-training-load check, see class Javadoc "Training-load
     * awareness". Any failure (no data yet, or the collaborator throwing)
     * degrades to "no adjustment" — same graceful-degradation convention
     * {@code SleepInsightService#computeSleepPlan}'s own training-load
     * composition already uses (parity-matrix row 20) — never a fabricated
     * adjustment.
     */
    private LoadAdjustment computeLoadAdjustment(Long accountId) {
        try {
            TrainingLoadSummary summary = trainingLoadService.computeSummary(accountId);
            if (HIGH_LOAD_STATUS.equals(summary.loadStatus()) && summary.acwr() != null) {
                return new LoadAdjustment(true, summary.acwr());
            }
            return LoadAdjustment.none();
        } catch (Exception e) {
            log.warn("Failed to fetch training load for account {}: {}", accountId, e.getMessage(), e);
            return LoadAdjustment.none();
        }
    }

    private record LoadAdjustment(boolean apply, double acwr) {
        static LoadAdjustment none() {
            return new LoadAdjustment(false, 0.0);
        }
    }

    private List<WorkoutExercise> selectCandidates(
            ExerciseCategory category, Goal goal, Set<Equipment> equipment, Set<Limitation> limitations,
            int count, IntensityProfile profile
    ) {
        List<WorkoutExercise> result = new ArrayList<>();
        for (ExerciseDefinition def : WorkoutExerciseLibrary.LIBRARY) {
            if (result.size() >= count) {
                break;
            }
            if (def.category() != category) {
                continue;
            }
            if (!def.goals().contains(goal)) {
                continue;
            }
            if (!isAvailable(def, equipment)) {
                continue;
            }
            if (!Collections.disjoint(def.contraindications(), limitations)) {
                continue;
            }
            result.add(new WorkoutExercise(
                    def.name(), profile.sets(), profile.repsOrDuration(), profile.restSeconds(), def.requiredEquipment()));
        }
        return result;
    }

    /**
     * Bodyweight is always implicitly available (no equipment is never a
     * real-world blocker); anything else must be explicitly present in the
     * request's equipment set. See class Javadoc and {@code
     * Equipment#BODYWEIGHT}'s Javadoc.
     */
    private boolean isAvailable(ExerciseDefinition def, Set<Equipment> requestEquipment) {
        return def.requiredEquipment() == Equipment.BODYWEIGHT || requestEquipment.contains(def.requiredEquipment());
    }

    private void noteShortfall(List<String> limitationsOut, String blockName, int requested, int actual) {
        if (actual < requested) {
            limitationsOut.add(String.format(
                    "Only found %d of the usual %d %s exercise(s) in the curated library matching your goal, "
                            + "equipment, and limitations - shown honestly as a shorter block rather than repeating "
                            + "one or padding the count.",
                    actual, requested, blockName));
        }
    }

    private IntensityProfile mainProfileFor(Goal goal) {
        return switch (goal) {
            case STRENGTH -> new IntensityProfile(4, "6-8 reps", 90);
            case ENDURANCE -> new IntensityProfile(3, "15-20 reps", 30);
            case MOBILITY_RECOVERY -> new IntensityProfile(2, "30-45 sec hold", 20);
            case GENERAL_FITNESS -> new IntensityProfile(3, "10-12 reps", 60);
        };
    }

    private String goalLabel(Goal goal) {
        return switch (goal) {
            case STRENGTH -> "strength";
            case ENDURANCE -> "endurance";
            case MOBILITY_RECOVERY -> "mobility/recovery";
            case GENERAL_FITNESS -> "general fitness";
        };
    }

    private static final IntensityProfile WARMUP_PROFILE = new IntensityProfile(1, "30-45 sec", 15);
    private static final IntensityProfile COOLDOWN_PROFILE = new IntensityProfile(1, "30-45 sec hold", 15);

    private record IntensityProfile(int sets, String repsOrDuration, int restSeconds) {}

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
