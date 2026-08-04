import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../widgets/confidence_banner.dart';
import '../../widgets/state_views.dart';

/// "Strength Activity Time" trend — closes parity-matrix row 25.
///
/// Combines real Garmin-derived session minutes with manually-logged
/// workout minutes, always shown as two separate segments per bucket
/// (never blended into one unlabeled bar) — see StrengthTrainingService
/// on the backend.
class StrengthTrendPage extends StatefulWidget {
  const StrengthTrendPage({super.key});

  @override
  State<StrengthTrendPage> createState() => _StrengthTrendPageState();
}

class _StrengthTrendPageState extends State<StrengthTrendPage> {
  final _apiClient = ApiClient();
  String _window = 'weekly';
  StrengthActivityTrendResponse? _trend;
  List<StrengthWorkoutResponse> _recentWorkouts = [];
  bool _loading = true;
  bool _trendLoading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadAll();
  }

  Future<void> _loadAll() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final trend = await _apiClient.getStrengthTrends(window: _window);
      final workouts = await _apiClient.getStrengthWorkouts();
      if (!mounted) return;
      setState(() {
        _trend = trend;
        _recentWorkouts = workouts;
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

  Future<void> _changeWindow(String window) async {
    setState(() {
      _window = window;
      _trendLoading = true;
    });
    try {
      final trend = await _apiClient.getStrengthTrends(window: window);
      if (!mounted) return;
      setState(() {
        _trend = trend;
        _trendLoading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _trendLoading = false);
    }
  }

  Future<void> _editGoal() async {
    final controller = TextEditingController(text: _trend?.weeklyGoalMinutes?.toString() ?? '');
    final result = await showDialog<int?>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Weekly strength goal'),
        content: TextField(
          controller: controller,
          keyboardType: TextInputType.number,
          autofocus: true,
          decoration: const InputDecoration(labelText: 'Minutes per week', helperText: 'Leave blank to clear the goal.'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancel')),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(int.tryParse(controller.text.trim())),
            child: const Text('Save'),
          ),
        ],
      ),
    );
    if (result == null && controller.text.trim().isNotEmpty) return; // dialog cancelled
    try {
      final updated = await _apiClient.setStrengthGoalMinutes(result);
      if (!mounted) return;
      setState(() {
        _trend = _trend == null
            ? null
            : StrengthActivityTrendResponse(
                window: _trend!.window,
                points: _trend!.points,
                weeklyGoalMinutes: updated,
                limitations: _trend!.limitations,
              );
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Could not save goal: $e')));
    }
  }

  Future<void> _openLogPage() async {
    final added = await context.push<bool>('/activities/strength/log');
    if (added == true) _loadAll();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Strength training'),
        actions: [IconButton(icon: const Icon(Icons.refresh), tooltip: 'Refresh', onPressed: _loadAll)],
      ),
      body: _buildBody(),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _openLogPage,
        icon: const Icon(Icons.add),
        label: const Text('Log workout'),
      ),
    );
  }

  Widget _buildBody() {
    if (_loading) return const LoadingView(label: 'Loading strength training data…');
    if (_error != null) return ErrorView(message: _error!, onRetry: _loadAll);
    final trend = _trend;
    if (trend == null) return const EmptyView(icon: Icons.fitness_center_outlined, title: 'No data');

    return RefreshIndicator(
      onRefresh: _loadAll,
      child: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: [
          ConfidenceBanner(confidence: 'medium', limitations: trend.limitations),
          _GoalCard(trend: trend, onEditGoal: _editGoal),
          const SizedBox(height: AppSpacing.lg),
          _TrendCard(
            trend: trend,
            window: _window,
            loading: _trendLoading,
            onWindowChanged: _changeWindow,
          ),
          if (_recentWorkouts.isNotEmpty) ...[
            const SizedBox(height: AppSpacing.lg),
            _RecentWorkoutsCard(workouts: _recentWorkouts),
          ],
        ],
      ),
    );
  }
}

class _GoalCard extends StatelessWidget {
  final StrengthActivityTrendResponse trend;
  final VoidCallback onEditGoal;
  const _GoalCard({required this.trend, required this.onEditGoal});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final goal = trend.weeklyGoalMinutes;
    // Most recent weekly-equivalent figure available regardless of the
    // currently-selected window: the last point's totalMinutes. Only a
    // literal apples-to-apples comparison when window is 'weekly' — the
    // subtitle says so.
    final latest = trend.points.isNotEmpty ? trend.points.last.totalMinutes : null;

    return Card(
      child: ListTile(
        leading: Icon(Icons.flag_outlined, color: theme.colorScheme.primary),
        title: Text(goal != null ? 'Weekly goal: $goal min' : 'No weekly goal set'),
        subtitle: (goal != null && latest != null && trend.window == 'weekly')
            ? Text('${latest.toStringAsFixed(0)} of $goal min this week')
            : const Text('Optional — tap to set a weekly minutes target'),
        trailing: const Icon(Icons.edit_outlined, size: 20),
        onTap: onEditGoal,
      ),
    );
  }
}

class _TrendCard extends StatelessWidget {
  final StrengthActivityTrendResponse trend;
  final String window;
  final bool loading;
  final ValueChanged<String> onWindowChanged;

  const _TrendCard({
    required this.trend,
    required this.window,
    required this.loading,
    required this.onWindowChanged,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final points = trend.points;
    final maxTotal = points.isEmpty ? 0.0 : points.map((p) => p.totalMinutes).reduce((a, b) => a > b ? a : b);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text('Strength Activity Time', style: theme.textTheme.titleMedium),
                const Spacer(),
                SegmentedButton<String>(
                  segments: const [
                    ButtonSegment(value: 'weekly', label: Text('Weekly')),
                    ButtonSegment(value: 'monthly', label: Text('Monthly')),
                    ButtonSegment(value: 'sixmonth', label: Text('6mo')),
                  ],
                  selected: {window},
                  showSelectedIcon: false,
                  onSelectionChanged: loading ? null : (s) => onWindowChanged(s.first),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.md),
            if (loading)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: AppSpacing.lg),
                child: Center(child: CircularProgressIndicator()),
              )
            else if (points.isEmpty)
              Text('No strength training in this window yet.', style: theme.textTheme.bodySmall)
            else ...[
              Row(
                children: [
                  _LegendDot(color: theme.colorScheme.primary, label: 'Garmin-measured'),
                  const SizedBox(width: AppSpacing.lg),
                  _LegendDot(color: theme.status.fair, label: 'Manually logged'),
                ],
              ),
              const SizedBox(height: AppSpacing.md),
              ...points.map((p) => _TrendBar(point: p, maxTotal: maxTotal)),
            ],
          ],
        ),
      ),
    );
  }
}

class _LegendDot extends StatelessWidget {
  final Color color;
  final String label;
  const _LegendDot({required this.color, required this.label});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(width: 10, height: 10, decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
        const SizedBox(width: AppSpacing.xs),
        Text(label, style: Theme.of(context).textTheme.bodySmall),
      ],
    );
  }
}

class _TrendBar extends StatelessWidget {
  final StrengthActivityTrendPointResponse point;
  final double maxTotal;
  const _TrendBar({required this.point, required this.maxTotal});

  // Flex values are integers, so fractional minutes are scaled up before
  // rounding — precision doesn't matter here, this only drives relative
  // segment widths in a bar, not any numeric total shown to the user.
  static const int _flexScale = 1000;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final garminFlex = maxTotal > 0 ? (point.garminMinutes / maxTotal * _flexScale).round() : 0;
    final manualFlex = maxTotal > 0 ? (point.manualMinutes / maxTotal * _flexScale).round() : 0;
    final remainderFlex = (_flexScale - garminFlex - manualFlex).clamp(0, _flexScale);

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
      child: Row(
        children: [
          SizedBox(width: 64, child: Text(point.periodStart.substring(5), style: theme.textTheme.bodySmall)),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: ClipRRect(
              borderRadius: BorderRadius.circular(AppRadius.pill),
              child: SizedBox(
                height: 10,
                child: (garminFlex + manualFlex) == 0
                    ? Container(color: theme.colorScheme.surfaceContainerHighest)
                    : Row(
                        children: [
                          if (garminFlex > 0) Expanded(flex: garminFlex, child: Container(color: theme.colorScheme.primary)),
                          if (manualFlex > 0) Expanded(flex: manualFlex, child: Container(color: theme.status.fair)),
                          if (remainderFlex > 0)
                            Expanded(flex: remainderFlex, child: Container(color: theme.colorScheme.surfaceContainerHighest)),
                        ],
                      ),
              ),
            ),
          ),
          const SizedBox(width: AppSpacing.sm),
          SizedBox(
            width: 56,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                Text('${point.totalMinutes.toStringAsFixed(0)}m', style: theme.textTheme.labelLarge),
                if (point.manualMinutesEstimated) ...[
                  const SizedBox(width: 2),
                  Tooltip(
                    message: 'Includes an estimated duration for a manually-logged workout',
                    child: Icon(Icons.info_outline, size: 12, color: theme.colorScheme.onSurfaceVariant),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _RecentWorkoutsCard extends StatelessWidget {
  final List<StrengthWorkoutResponse> workouts;
  const _RecentWorkoutsCard({required this.workouts});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Recent workouts', style: theme.textTheme.titleMedium),
            const SizedBox(height: AppSpacing.xs),
            ...workouts.map((w) => _WorkoutTile(workout: w)),
          ],
        ),
      ),
    );
  }
}

class _WorkoutTile extends StatelessWidget {
  final StrengthWorkoutResponse workout;
  const _WorkoutTile({required this.workout});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final started = DateTime.tryParse(workout.startedAt);
    final dateLabel = started != null ? '${started.month}/${started.day}' : '';
    final exerciseNames = workout.exercises.map((e) => e.exerciseName).join(', ');
    final durationLabel = workout.durationIsEstimated
        ? '~${workout.durationMinutes}m (estimated)'
        : '${workout.durationMinutes}m';

    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: CircleAvatar(
        backgroundColor: theme.colorScheme.primaryContainer,
        child: Icon(Icons.fitness_center, color: theme.colorScheme.onPrimaryContainer, size: 20),
      ),
      title: Text(exerciseNames.isEmpty ? 'Workout' : exerciseNames, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Text('$dateLabel · ${workout.totalSets} sets · $durationLabel'),
    );
  }
}
