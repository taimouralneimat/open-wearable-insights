import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../app/app.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../widgets/confidence_banner.dart';
import '../../widgets/state_views.dart';

/// Activities page — shows daily activity summary and 7-day trends.
class ActivitiesPage extends StatefulWidget {
  const ActivitiesPage({super.key});

  @override
  State<ActivitiesPage> createState() => _ActivitiesPageState();
}

class _ActivitiesPageState extends State<ActivitiesPage> {
  final _apiClient = ApiClient();
  ActivitySummary? _summary;
  List<ActivityTrendPoint>? _trends;
  List<ActivitySessionResponse> _sessions = [];
  int _trendWindowDays = 7;
  bool _loading = true;
  bool _trendsLoading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final summary = await _apiClient.getActivitySummary();
      final trends = await _apiClient.getActivityTrends(days: _trendWindowDays);
      final sessions = await _apiClient.getActivitySessions();
      setState(() {
        _summary = summary;
        _trends = trends;
        _sessions = sessions;
        _loading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  Future<void> _changeTrendWindow(int days) async {
    setState(() {
      _trendWindowDays = days;
      _trendsLoading = true;
    });
    try {
      final trends = await _apiClient.getActivityTrends(days: days);
      if (!mounted) return;
      setState(() {
        _trends = trends;
        _trendsLoading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _trendsLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Activity'),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), tooltip: 'Refresh', onPressed: _loadData),
          const MoreMenuButton(),
          const SizedBox(width: AppSpacing.xs),
        ],
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading) return const LoadingView(label: 'Loading activity data…');
    if (_error != null) return ErrorView(message: _error!, onRetry: _loadData);
    if (_summary == null) {
      return EmptyView(
        icon: Icons.directions_run_outlined,
        title: 'No activity data yet',
        message: 'Import wearable data to see steps and trends.',
        action: FilledButton.icon(
          onPressed: () => context.push('/import'),
          icon: const Icon(Icons.upload_file_outlined, size: 18),
          label: const Text('Import data'),
        ),
      );
    }
    return _buildPopulated();
  }

  Widget _buildPopulated() {
    final s = _summary!;
    return RefreshIndicator(
      onRefresh: _loadData,
      child: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: [
          ConfidenceBanner(confidence: s.confidence, limitations: s.limitations),
          _ActivitySummaryCard(summary: s),
          const SizedBox(height: AppSpacing.lg),
          _TrainingLoadEntryCard(),
          const SizedBox(height: AppSpacing.lg),
          _StrengthEntryCard(),
          const SizedBox(height: AppSpacing.lg),
          if (_trends != null)
            _ActivityTrendsCard(
              trends: _trends!,
              windowDays: _trendWindowDays,
              loading: _trendsLoading,
              onWindowChanged: _changeTrendWindow,
            ),
          if (_sessions.isNotEmpty) ...[
            const SizedBox(height: AppSpacing.lg),
            _RecentSessionsCard(sessions: _sessions),
          ],
        ],
      ),
    );
  }
}

/// Lightweight entry point into the full training-load view — fetches its
/// own summary so it can show a live at-a-glance status without coupling
/// to the parent page's load sequence.
class _TrainingLoadEntryCard extends StatefulWidget {
  @override
  State<_TrainingLoadEntryCard> createState() => _TrainingLoadEntryCardState();
}

class _TrainingLoadEntryCardState extends State<_TrainingLoadEntryCard> {
  final _apiClient = ApiClient();
  TrainingLoadSummaryResponse? _summary;

  @override
  void initState() {
    super.initState();
    _apiClient.getTrainingLoadSummary().then((s) {
      if (mounted) setState(() => _summary = s);
    }).catchError((_) {});
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final summary = _summary;
    return Card(
      child: ListTile(
        leading: Icon(Icons.speed_outlined, color: theme.colorScheme.primary),
        title: const Text('Training load'),
        subtitle: Text(
          summary != null
              ? (summary.acwr != null
                  ? 'ACWR ${summary.acwr!.toStringAsFixed(2)} · ${summary.loadStatus}'
                  : 'Not enough recent activity data yet')
              : 'Loading…',
        ),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => context.push('/activities/training-load'),
      ),
    );
  }
}

/// Lightweight entry point into the Strength Activity Time trend view
/// (parity-matrix row 25) — same fetch-its-own-summary pattern as
/// [_TrainingLoadEntryCard].
class _StrengthEntryCard extends StatefulWidget {
  @override
  State<_StrengthEntryCard> createState() => _StrengthEntryCardState();
}

class _StrengthEntryCardState extends State<_StrengthEntryCard> {
  final _apiClient = ApiClient();
  StrengthActivityTrendResponse? _trend;

  @override
  void initState() {
    super.initState();
    _apiClient.getStrengthTrends(window: 'weekly').then((t) {
      if (mounted) setState(() => _trend = t);
    }).catchError((_) {});
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final trend = _trend;
    final weekMinutes = trend != null && trend.points.isNotEmpty ? trend.points.last.totalMinutes : null;
    return Card(
      child: ListTile(
        leading: Icon(Icons.fitness_center_outlined, color: theme.colorScheme.primary),
        title: const Text('Strength training'),
        subtitle: Text(
          weekMinutes != null
              ? '${weekMinutes.toStringAsFixed(0)} min this week'
              : (trend != null ? 'No strength training logged this week' : 'Loading…'),
        ),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => context.push('/activities/strength'),
      ),
    );
  }
}

class _ActivitySummaryCard extends StatelessWidget {
  final ActivitySummary summary;
  const _ActivitySummaryCard({required this.summary});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.xl, horizontal: AppSpacing.lg),
        child: Column(
          children: [
            Text('TODAY', style: theme.textTheme.labelSmall),
            const SizedBox(height: AppSpacing.lg),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                _StatTile(icon: Icons.directions_walk, label: 'Steps', value: '${summary.steps}', color: scheme.primary),
                _StatTile(
                  icon: Icons.local_fire_department_outlined,
                  label: 'Calories',
                  value: summary.calories?.toString() ?? '—',
                  color: theme.status.fair,
                ),
                _StatTile(
                  icon: Icons.timer_outlined,
                  label: 'Active min',
                  value: summary.activeMinutes?.toString() ?? '—',
                  color: scheme.tertiary,
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.lg),
            const Divider(height: 1),
            const SizedBox(height: AppSpacing.md),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.favorite, size: 16, color: theme.status.poor),
                const SizedBox(width: AppSpacing.sm),
                Text(
                  summary.activeZoneMinutes != null
                      ? '${summary.activeZoneMinutes} active zone minutes'
                      : 'Active zone minutes not tracked',
                  style: theme.textTheme.bodyMedium,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _StatTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final Color color;
  const _StatTile({
    required this.icon,
    required this.label,
    required this.value,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      children: [
        Icon(icon, color: color, size: 28),
        const SizedBox(height: AppSpacing.xs),
        Text(value, style: theme.textTheme.headlineSmall?.copyWith(color: color)),
        Text(label, style: theme.textTheme.bodySmall),
      ],
    );
  }
}

class _ActivityTrendsCard extends StatelessWidget {
  final List<ActivityTrendPoint> trends;
  final int windowDays;
  final bool loading;
  final ValueChanged<int> onWindowChanged;

  const _ActivityTrendsCard({
    required this.trends,
    required this.windowDays,
    required this.loading,
    required this.onWindowChanged,
  });

  static const List<int> _windowOptions = [7, 30, 90];
  // Same reasoning as _SleepTrendsCard: a per-day bar list is only readable
  // up to a couple weeks — beyond that the average above carries the signal.
  static const int _maxRowsShown = 14;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final shown = trends.length > _maxRowsShown
        ? trends.sublist(trends.length - _maxRowsShown)
        : trends;
    final maxSteps = shown.isEmpty ? 0 : shown.map((t) => t.steps).reduce((a, b) => a > b ? a : b);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text('Steps trend', style: theme.textTheme.titleMedium),
                const Spacer(),
                SegmentedButton<int>(
                  segments: _windowOptions
                      .map((d) => ButtonSegment(value: d, label: Text('${d}d')))
                      .toList(),
                  selected: {windowDays},
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
            else if (trends.isEmpty)
              Text('No days in this window yet.', style: theme.textTheme.bodySmall)
            else ...[
              if (windowDays > 7) ...[
                _ActivityTrendSummaryRow(trends: trends),
                const SizedBox(height: AppSpacing.md),
                if (trends.length > _maxRowsShown)
                  Padding(
                    padding: const EdgeInsets.only(bottom: AppSpacing.sm),
                    child: Text(
                      'Most recent $_maxRowsShown of ${trends.length} days:',
                      style: theme.textTheme.bodySmall,
                    ),
                  ),
              ],
              ...shown.map((t) => _TrendBar(point: t, maxSteps: maxSteps)),
            ],
          ],
        ),
      ),
    );
  }
}

class _ActivityTrendSummaryRow extends StatelessWidget {
  final List<ActivityTrendPoint> trends;
  const _ActivityTrendSummaryRow({required this.trends});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final avgSteps = trends.map((t) => t.steps).reduce((a, b) => a + b) / trends.length;
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        '${avgSteps.round()} avg steps/day over ${trends.length} days',
        style: theme.textTheme.bodyMedium,
      ),
    );
  }
}

class _RecentSessionsCard extends StatelessWidget {
  final List<ActivitySessionResponse> sessions;
  const _RecentSessionsCard({required this.sessions});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Recent sessions', style: theme.textTheme.titleMedium),
            const SizedBox(height: AppSpacing.xs),
            ...sessions.map((s) => _SessionTile(session: s)),
          ],
        ),
      ),
    );
  }
}

class _SessionTile extends StatelessWidget {
  final ActivitySessionResponse session;
  const _SessionTile({required this.session});

  IconData get _icon => switch (session.sport) {
        'running' => Icons.directions_run,
        'walking' => Icons.directions_walk,
        'cycling' => Icons.directions_bike,
        _ => Icons.fitness_center,
      };

  String get _sportLabel => session.sport.isEmpty ? 'Activity' : session.sport[0].toUpperCase() + session.sport.substring(1);

  String get _distanceLabel {
    if (session.distanceMeters == null) return '';
    return ' · ${(session.distanceMeters! / 1000).toStringAsFixed(2)} km';
  }

  String get _durationLabel {
    final total = session.durationSeconds.round();
    final m = total ~/ 60;
    return '${m}m';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final start = DateTime.tryParse(session.startTime);
    final dateLabel = start != null ? '${start.month}/${start.day}' : '';
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: CircleAvatar(
        backgroundColor: theme.colorScheme.primaryContainer,
        child: Icon(_icon, color: theme.colorScheme.onPrimaryContainer, size: 20),
      ),
      title: Text(_sportLabel, style: theme.textTheme.bodyLarge),
      subtitle: Text('$dateLabel · $_durationLabel$_distanceLabel', style: theme.textTheme.bodySmall),
      trailing: session.avgHeartRate != null
          ? Text('${session.avgHeartRate} bpm', style: theme.textTheme.labelLarge)
          : null,
      onTap: () => context.push('/activities/sessions/${session.id}'),
    );
  }
}

class _TrendBar extends StatelessWidget {
  final ActivityTrendPoint point;
  final int maxSteps;
  const _TrendBar({required this.point, required this.maxSteps});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final fraction = maxSteps > 0 ? (point.steps / maxSteps).clamp(0.0, 1.0) : 0.0;
    final color = point.steps >= 10000
        ? theme.status.good
        : point.steps >= 7000
            ? scheme.primary
            : theme.status.fair;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
      child: Row(
        children: [
          SizedBox(width: 44, child: Text(point.date.substring(5), style: theme.textTheme.bodySmall)),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: ClipRRect(
              borderRadius: BorderRadius.circular(AppRadius.pill),
              child: LinearProgressIndicator(value: fraction, color: color, minHeight: 10),
            ),
          ),
          const SizedBox(width: AppSpacing.sm),
          SizedBox(
            width: 52,
            child: Text('${point.steps}', style: theme.textTheme.labelLarge, textAlign: TextAlign.right),
          ),
        ],
      ),
    );
  }
}
