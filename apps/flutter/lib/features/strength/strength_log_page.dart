import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';

/// Log a strength workout — add exercises, each with sets of reps/weight.
///
/// Duration is optional: when left blank, the trend view estimates one
/// from set count rather than treating the workout as unmeasured (see
/// StrengthTrainingService on the backend) — the hint text below the field
/// says so explicitly.
class StrengthLogPage extends StatefulWidget {
  const StrengthLogPage({super.key});

  @override
  State<StrengthLogPage> createState() => _StrengthLogPageState();
}

class _ExerciseFormEntry {
  final TextEditingController nameController = TextEditingController();
  final List<_SetFormEntry> sets = [_SetFormEntry()];
}

class _SetFormEntry {
  final TextEditingController repsController = TextEditingController();
  final TextEditingController weightController = TextEditingController();
}

class _StrengthLogPageState extends State<StrengthLogPage> {
  final _apiClient = ApiClient();
  final _noteController = TextEditingController();
  final _durationController = TextEditingController();
  DateTime _startedAt = DateTime.now();
  final List<_ExerciseFormEntry> _exercises = [_ExerciseFormEntry()];
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _noteController.dispose();
    _durationController.dispose();
    for (final exercise in _exercises) {
      exercise.nameController.dispose();
      for (final set in exercise.sets) {
        set.repsController.dispose();
        set.weightController.dispose();
      }
    }
    super.dispose();
  }

  void _addExercise() => setState(() => _exercises.add(_ExerciseFormEntry()));

  void _removeExercise(int index) => setState(() => _exercises.removeAt(index));

  void _addSet(_ExerciseFormEntry exercise) => setState(() => exercise.sets.add(_SetFormEntry()));

  void _removeSet(_ExerciseFormEntry exercise, int index) => setState(() => exercise.sets.removeAt(index));

  Future<void> _pickStartedAt() async {
    final date = await showDatePicker(
      context: context,
      initialDate: _startedAt,
      firstDate: DateTime(2015),
      lastDate: DateTime.now(),
    );
    if (date == null || !mounted) return;
    final time = await showTimePicker(context: context, initialTime: TimeOfDay.fromDateTime(_startedAt));
    if (time == null) return;
    setState(() {
      _startedAt = DateTime(date.year, date.month, date.day, time.hour, time.minute);
    });
  }

  bool get _canSave {
    for (final exercise in _exercises) {
      if (exercise.nameController.text.trim().isEmpty) return false;
      for (final set in exercise.sets) {
        if (int.tryParse(set.repsController.text.trim()) == null) return false;
      }
    }
    return _exercises.isNotEmpty;
  }

  Future<void> _save() async {
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final exercises = _exercises
          .map((e) => StrengthExerciseInput(
                exerciseName: e.nameController.text.trim(),
                sets: e.sets
                    .map((s) => StrengthSetInput(
                          reps: int.parse(s.repsController.text.trim()),
                          weightKg: double.tryParse(s.weightController.text.trim()),
                        ))
                    .toList(),
              ))
          .toList();
      final durationMinutes = int.tryParse(_durationController.text.trim());
      await _apiClient.logStrengthWorkout(
        startedAt: _startedAt,
        durationMinutes: durationMinutes,
        note: _noteController.text.trim().isEmpty ? null : _noteController.text.trim(),
        exercises: exercises,
      );
      if (!mounted) return;
      context.pop(true);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString();
        _saving = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Log strength workout')),
      body: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(AppSpacing.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.event_outlined),
                    title: const Text('Date & time'),
                    subtitle: Text('${_startedAt.year}-${_two(_startedAt.month)}-${_two(_startedAt.day)} '
                        '${_two(_startedAt.hour)}:${_two(_startedAt.minute)}'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: _pickStartedAt,
                  ),
                  const SizedBox(height: AppSpacing.sm),
                  TextField(
                    controller: _durationController,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(
                      labelText: 'Duration (minutes) — optional',
                      helperText: 'Leave blank to estimate from set count in trend views.',
                      helperMaxLines: 2,
                    ),
                  ),
                  const SizedBox(height: AppSpacing.sm),
                  TextField(
                    controller: _noteController,
                    decoration: const InputDecoration(labelText: 'Note — optional'),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
          for (int i = 0; i < _exercises.length; i++) ...[
            _ExerciseCard(
              entry: _exercises[i],
              onRemove: _exercises.length > 1 ? () => _removeExercise(i) : null,
              onAddSet: () => _addSet(_exercises[i]),
              onRemoveSet: (setIndex) => _removeSet(_exercises[i], setIndex),
              onChanged: () => setState(() {}),
            ),
            const SizedBox(height: AppSpacing.md),
          ],
          OutlinedButton.icon(
            onPressed: _addExercise,
            icon: const Icon(Icons.add),
            label: const Text('Add exercise'),
          ),
          if (_error != null) ...[
            const SizedBox(height: AppSpacing.md),
            Text(_error!, style: theme.textTheme.bodySmall?.copyWith(color: theme.status.poor)),
          ],
          const SizedBox(height: AppSpacing.xl),
          FilledButton(
            onPressed: (_saving || !_canSave) ? null : _save,
            child: _saving
                ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                : const Text('Save workout'),
          ),
        ],
      ),
    );
  }

  String _two(int n) => n.toString().padLeft(2, '0');
}

class _ExerciseCard extends StatelessWidget {
  final _ExerciseFormEntry entry;
  final VoidCallback? onRemove;
  final VoidCallback onAddSet;
  final ValueChanged<int> onRemoveSet;
  final VoidCallback onChanged;

  const _ExerciseCard({
    required this.entry,
    required this.onRemove,
    required this.onAddSet,
    required this.onRemoveSet,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: entry.nameController,
                    decoration: const InputDecoration(labelText: 'Exercise name'),
                    onChanged: (_) => onChanged(),
                  ),
                ),
                if (onRemove != null)
                  IconButton(icon: const Icon(Icons.close), tooltip: 'Remove exercise', onPressed: onRemove),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            for (int i = 0; i < entry.sets.length; i++)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
                child: Row(
                  children: [
                    SizedBox(width: 28, child: Text('${i + 1}', style: Theme.of(context).textTheme.bodySmall)),
                    Expanded(
                      child: TextField(
                        controller: entry.sets[i].repsController,
                        keyboardType: TextInputType.number,
                        decoration: const InputDecoration(labelText: 'Reps', isDense: true),
                        onChanged: (_) => onChanged(),
                      ),
                    ),
                    const SizedBox(width: AppSpacing.sm),
                    Expanded(
                      child: TextField(
                        controller: entry.sets[i].weightController,
                        keyboardType: const TextInputType.numberWithOptions(decimal: true),
                        decoration: const InputDecoration(labelText: 'Weight (kg) — optional', isDense: true),
                      ),
                    ),
                    if (entry.sets.length > 1)
                      IconButton(
                        icon: const Icon(Icons.remove_circle_outline, size: 20),
                        tooltip: 'Remove set',
                        onPressed: () => onRemoveSet(i),
                      ),
                  ],
                ),
              ),
            TextButton.icon(
              onPressed: onAddSet,
              icon: const Icon(Icons.add, size: 18),
              label: const Text('Add set'),
            ),
          ],
        ),
      ),
    );
  }
}
