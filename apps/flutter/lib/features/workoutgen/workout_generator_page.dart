import 'package:flutter/material.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';

/// Deterministic custom workout generator — closes parity-matrix row 26.
///
/// Backed by the real `POST /api/v1/workout-gen/generate` endpoint
/// (workoutgen-v1). No LLM is called: this app's own curated, versioned
/// exercise template library is matched against the stated goal, equipment,
/// and limitations, entirely deterministically — the same inputs always
/// produce the same workout. The response's disclaimer/source are shown
/// verbatim, never summarized away, per the acceptance criteria's "clearly
/// labeled as such, not a prescription."
class WorkoutGeneratorPage extends StatefulWidget {
  const WorkoutGeneratorPage({super.key});

  @override
  State<WorkoutGeneratorPage> createState() => _WorkoutGeneratorPageState();
}

class _WorkoutGeneratorPageState extends State<WorkoutGeneratorPage> {
  final _apiClient = ApiClient();

  static const _goals = [
    ('STRENGTH', 'Strength'),
    ('ENDURANCE', 'Endurance'),
    ('MOBILITY_RECOVERY', 'Mobility / recovery'),
    ('GENERAL_FITNESS', 'General fitness'),
  ];

  static const _equipmentOptions = [
    ('DUMBBELLS', 'Dumbbells'),
    ('KETTLEBELL', 'Kettlebell'),
    ('BARBELL', 'Barbell'),
    ('RESISTANCE_BAND', 'Resistance band'),
    ('FULL_GYM', 'Full gym / machines'),
  ];

  static const _limitationOptions = [
    ('KNEE', 'Knee'),
    ('SHOULDER', 'Shoulder'),
    ('LOWER_BACK', 'Lower back'),
    ('WRIST', 'Wrist'),
  ];

  String _goal = 'GENERAL_FITNESS';
  final Set<String> _equipment = {};
  final Set<String> _limitations = {};
  double _duration = 30;

  GeneratedWorkoutResponse? _workout;
  bool _loading = false;
  String? _error;

  Future<void> _generate() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final workout = await _apiClient.generateWorkout(
        goal: _goal,
        equipment: _equipment.toList(),
        limitations: _limitations.toList(),
        durationMinutes: _duration.round(),
      );
      if (!mounted) return;
      setState(() {
        _workout = workout;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Workout generator')),
      body: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: [
          _FormCard(
            goal: _goal,
            goals: _goals,
            onGoalChanged: (g) => setState(() => _goal = g),
            equipment: _equipment,
            equipmentOptions: _equipmentOptions,
            onEquipmentToggled: (id, selected) => setState(() {
              if (selected) {
                _equipment.add(id);
              } else {
                _equipment.remove(id);
              }
            }),
            limitations: _limitations,
            limitationOptions: _limitationOptions,
            onLimitationToggled: (id, selected) => setState(() {
              if (selected) {
                _limitations.add(id);
              } else {
                _limitations.remove(id);
              }
            }),
            duration: _duration,
            onDurationChanged: (v) => setState(() => _duration = v),
          ),
          const SizedBox(height: AppSpacing.lg),
          FilledButton.icon(
            onPressed: _loading ? null : _generate,
            icon: _loading
                ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                : const Icon(Icons.auto_awesome_outlined),
            label: Text(_workout == null ? 'Generate workout' : 'Regenerate'),
          ),
          if (_error != null) ...[
            const SizedBox(height: AppSpacing.md),
            Text(_error!, style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Theme.of(context).status.poor)),
          ],
          if (_workout != null) ...[
            const SizedBox(height: AppSpacing.xl),
            _WorkoutResult(workout: _workout!),
          ],
        ],
      ),
    );
  }
}

class _FormCard extends StatelessWidget {
  final String goal;
  final List<(String, String)> goals;
  final ValueChanged<String> onGoalChanged;
  final Set<String> equipment;
  final List<(String, String)> equipmentOptions;
  final void Function(String id, bool selected) onEquipmentToggled;
  final Set<String> limitations;
  final List<(String, String)> limitationOptions;
  final void Function(String id, bool selected) onLimitationToggled;
  final double duration;
  final ValueChanged<double> onDurationChanged;

  const _FormCard({
    required this.goal,
    required this.goals,
    required this.onGoalChanged,
    required this.equipment,
    required this.equipmentOptions,
    required this.onEquipmentToggled,
    required this.limitations,
    required this.limitationOptions,
    required this.onLimitationToggled,
    required this.duration,
    required this.onDurationChanged,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Goal', style: theme.textTheme.titleSmall),
            const SizedBox(height: AppSpacing.sm),
            Wrap(
              spacing: AppSpacing.sm,
              runSpacing: AppSpacing.sm,
              children: [
                for (final (id, label) in goals)
                  ChoiceChip(
                    label: Text(label),
                    selected: goal == id,
                    onSelected: (_) => onGoalChanged(id),
                  ),
              ],
            ),
            const SizedBox(height: AppSpacing.lg),
            Text('Equipment', style: theme.textTheme.titleSmall),
            const SizedBox(height: 2),
            Text('Bodyweight exercises are always included — add anything else you have access to.',
                style: theme.textTheme.bodySmall),
            const SizedBox(height: AppSpacing.sm),
            Wrap(
              spacing: AppSpacing.sm,
              runSpacing: AppSpacing.sm,
              children: [
                for (final (id, label) in equipmentOptions)
                  FilterChip(
                    label: Text(label),
                    selected: equipment.contains(id),
                    onSelected: (selected) => onEquipmentToggled(id, selected),
                  ),
              ],
            ),
            const SizedBox(height: AppSpacing.lg),
            Text('Limitations', style: theme.textTheme.titleSmall),
            const SizedBox(height: 2),
            Text('Exercises flagged as contraindicated for these will be excluded.', style: theme.textTheme.bodySmall),
            const SizedBox(height: AppSpacing.sm),
            Wrap(
              spacing: AppSpacing.sm,
              runSpacing: AppSpacing.sm,
              children: [
                for (final (id, label) in limitationOptions)
                  FilterChip(
                    label: Text(label),
                    selected: limitations.contains(id),
                    onSelected: (selected) => onLimitationToggled(id, selected),
                  ),
              ],
            ),
            const SizedBox(height: AppSpacing.lg),
            Row(
              children: [
                Text('Duration', style: theme.textTheme.titleSmall),
                const Spacer(),
                Text('${duration.round()} min', style: theme.textTheme.titleSmall),
              ],
            ),
            Slider(
              value: duration,
              min: 10,
              max: 90,
              divisions: 16,
              label: '${duration.round()} min',
              onChanged: onDurationChanged,
            ),
          ],
        ),
      ),
    );
  }
}

class _WorkoutResult extends StatelessWidget {
  final GeneratedWorkoutResponse workout;
  const _WorkoutResult({required this.workout});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _DisclaimerBanner(disclaimer: workout.disclaimer),
        if (workout.intensityAdjustment != null) ...[
          const SizedBox(height: AppSpacing.md),
          _IntensityAdjustmentBanner(text: workout.intensityAdjustment!),
        ],
        const SizedBox(height: AppSpacing.lg),
        Row(
          children: [
            Text('~${workout.durationMinutesEstimated} min plan', style: theme.textTheme.titleMedium),
            const Spacer(),
            Text('v${workout.algorithmVersion}', style: theme.textTheme.bodySmall),
          ],
        ),
        const SizedBox(height: AppSpacing.md),
        if (workout.warmup.isNotEmpty) _ExerciseBlockCard(title: 'Warmup', icon: Icons.whatshot_outlined, exercises: workout.warmup),
        if (workout.warmup.isNotEmpty) const SizedBox(height: AppSpacing.md),
        _ExerciseBlockCard(title: 'Main', icon: Icons.fitness_center, exercises: workout.main),
        if (workout.cooldown.isNotEmpty) const SizedBox(height: AppSpacing.md),
        if (workout.cooldown.isNotEmpty)
          _ExerciseBlockCard(title: 'Cooldown', icon: Icons.self_improvement_outlined, exercises: workout.cooldown),
        const SizedBox(height: AppSpacing.md),
        _LimitationsCard(limitations: workout.limitations),
      ],
    );
  }
}

class _DisclaimerBanner extends StatelessWidget {
  final String disclaimer;
  const _DisclaimerBanner({required this.disclaimer});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(AppRadius.md),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.info_outline, size: 18, color: theme.colorScheme.onSurfaceVariant),
          const SizedBox(width: AppSpacing.sm),
          Expanded(child: Text(disclaimer, style: theme.textTheme.bodySmall)),
        ],
      ),
    );
  }
}

/// Surfaces the real training-load-aware intensity reduction (see
/// WorkoutGeneratorService's "Training-load awareness") distinctly from the
/// generic disclaimer above — this is a real adjustment made to this
/// specific plan, not boilerplate.
class _IntensityAdjustmentBanner extends StatelessWidget {
  final String text;
  const _IntensityAdjustmentBanner({required this.text});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: status.fairContainer,
        borderRadius: BorderRadius.circular(AppRadius.md),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.battery_alert_outlined, size: 18, color: status.onFairContainer),
          const SizedBox(width: AppSpacing.sm),
          Expanded(child: Text(text, style: theme.textTheme.bodySmall?.copyWith(color: status.onFairContainer))),
        ],
      ),
    );
  }
}

class _ExerciseBlockCard extends StatelessWidget {
  final String title;
  final IconData icon;
  final List<WorkoutExerciseResponse> exercises;
  const _ExerciseBlockCard({required this.title, required this.icon, required this.exercises});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(icon, size: 18, color: theme.colorScheme.primary),
                const SizedBox(width: AppSpacing.sm),
                Text(title, style: theme.textTheme.titleMedium),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            if (exercises.isEmpty)
              Text('No matching exercises in the curated library for this combination.',
                  style: theme.textTheme.bodySmall)
            else
              for (final exercise in exercises) _ExerciseRow(exercise: exercise),
          ],
        ),
      ),
    );
  }
}

class _ExerciseRow extends StatelessWidget {
  final WorkoutExerciseResponse exercise;
  const _ExerciseRow({required this.exercise});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.sm),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(exercise.name, style: theme.textTheme.bodyLarge),
                Text(
                  '${exercise.sets} × ${exercise.repsOrDuration} · ${exercise.restSeconds}s rest',
                  style: theme.textTheme.bodySmall,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _LimitationsCard extends StatelessWidget {
  final List<String> limitations;
  const _LimitationsCard({required this.limitations});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Limitations', style: theme.textTheme.titleSmall),
            const SizedBox(height: AppSpacing.xs),
            ...limitations.map((l) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 2),
                  child: Text('• $l', style: theme.textTheme.bodySmall),
                )),
          ],
        ),
      ),
    );
  }
}
