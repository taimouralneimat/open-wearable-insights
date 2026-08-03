import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../app/app.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../widgets/confidence_banner.dart';
import '../../widgets/state_views.dart';

/// Sleep page — shows sleep summary, stage breakdown, and 7-day trends.
class SleepPage extends StatefulWidget {
  const SleepPage({super.key});

  @override
  State<SleepPage> createState() => _SleepPageState();
}

class _SleepPageState extends State<SleepPage> {
  final _apiClient = ApiClient();
  SleepSummary? _summary;
  List<SleepTrendPoint>? _trends;
  SleepDebtResponse? _debt;
  SleepPlanResponse? _plan;
  SleepConsistencyResponse? _consistency;
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
      final summary = await _apiClient.getSleepSummary();
      final trends = await _apiClient.getSleepTrends(days: _trendWindowDays);
      // Best-effort — debt is a secondary surface, shouldn't block the
      // main sleep summary from loading.
      SleepDebtResponse? debt;
      try { debt = await _apiClient.getSleepDebt(); } catch (_) { debt = null; }
      SleepPlanResponse? plan;
      try { plan = await _apiClient.getSleepPlan(); } catch (_) { plan = null; }
      SleepConsistencyResponse? consistency;
      try { consistency = await _apiClient.getSleepConsistency(); } catch (_) { consistency = null; }
      setState(() {
        _summary = summary;
        _trends = trends;
        _debt = debt;
        _plan = plan;
        _consistency = consistency;
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
      final trends = await _apiClient.getSleepTrends(days: days);
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
        title: const Text('Sleep'),
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
    if (_loading) return const LoadingView(label: 'Loading sleep data…');
    if (_error != null) return ErrorView(message: _error!, onRetry: _loadData);
    if (_summary == null) {
      return EmptyView(
        icon: Icons.bedtime_outlined,
        title: 'No sleep data yet',
        message: 'Import wearable data to see sleep stages and trends.',
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
          if (_plan != null && _plan!.recommendedBedtime != null) ...[
            _SleepPlanCard(plan: _plan!),
            const SizedBox(height: AppSpacing.lg),
          ],
          _SleepScoreCard(summary: s),
          const SizedBox(height: AppSpacing.lg),
          _SleepStagesCard(summary: s),
          const SizedBox(height: AppSpacing.lg),
          if (_debt != null) ...[
            _SleepDebtCard(debt: _debt!),
            const SizedBox(height: AppSpacing.lg),
          ],
          if (_consistency != null && _consistency!.consistencyScore != null) ...[
            _SleepConsistencyCard(consistency: _consistency!),
            const SizedBox(height: AppSpacing.lg),
          ],
          if (_trends != null)
            _SleepTrendsCard(
              trends: _trends!,
              windowDays: _trendWindowDays,
              loading: _trendsLoading,
              onWindowChanged: _changeTrendWindow,
            ),
        ],
      ),
    );
  }
}

/// Tonight's bedtime recommendation — parity row #20. Reasoning is always
/// shown alongside the time, not just a bare number.
class _SleepPlanCard extends StatelessWidget {
  final SleepPlanResponse plan;
  const _SleepPlanCard({required this.plan});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      color: theme.colorScheme.primaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(Icons.bedtime, color: theme.colorScheme.onPrimaryContainer),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Tonight: be asleep by ${plan.recommendedBedtime}',
                    style: theme.textTheme.titleMedium?.copyWith(color: theme.colorScheme.onPrimaryContainer),
                  ),
                  const SizedBox(height: AppSpacing.xs),
                  Text(
                    plan.reasoning,
                    style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onPrimaryContainer),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SleepScoreCard extends StatelessWidget {
  final SleepSummary summary;
  const _SleepScoreCard({required this.summary});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = theme.status.forScore(summary.sleepScore, goodAt: 80, fairAt: 60);
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.xl, horizontal: AppSpacing.lg),
        child: Column(
          children: [
            Text('LAST NIGHT', style: theme.textTheme.labelSmall),
            const SizedBox(height: AppSpacing.sm),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                Text('${summary.sleepScore}', style: theme.textTheme.displayLarge?.copyWith(color: color)),
                Text('/100', style: theme.textTheme.titleMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(
              summary.sleepNeedHours != null
                  ? '${summary.totalHours.toStringAsFixed(1)}h in bed · need ~${summary.sleepNeedHours!.toStringAsFixed(1)}h'
                  : '${summary.totalHours.toStringAsFixed(1)}h in bed',
              style: theme.textTheme.bodyLarge,
            ),
            if (summary.totalHours > 0) ...[
              const SizedBox(height: AppSpacing.xs),
              Text(
                '${summary.asleepHours.toStringAsFixed(1)}h actually asleep '
                '(${(summary.asleepHours / summary.totalHours * 100).round()}% of time in bed)',
                style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _SleepStagesCard extends StatelessWidget {
  final SleepSummary summary;
  const _SleepStagesCard({required this.summary});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Sleep stages', style: theme.textTheme.titleMedium),
            const SizedBox(height: AppSpacing.md),
            _StageBar(label: 'Deep', hours: summary.deepHours, color: scheme.primary),
            _StageBar(label: 'REM', hours: summary.remHours, color: scheme.tertiary),
            _StageBar(label: 'Light', hours: summary.lightHours, color: scheme.secondary),
            _StageBar(label: 'Awake', hours: summary.awakeHours, color: theme.status.fair),
          ],
        ),
      ),
    );
  }
}

class _StageBar extends StatelessWidget {
  final String label;
  final double hours;
  final Color color;
  const _StageBar({required this.label, required this.hours, required this.color});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    const maxHours = 4.0;
    final fraction = (hours / maxHours).clamp(0.0, 1.0);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
      child: Row(
        children: [
          SizedBox(width: 56, child: Text(label, style: theme.textTheme.bodyMedium)),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: ClipRRect(
              borderRadius: BorderRadius.circular(AppRadius.pill),
              child: LinearProgressIndicator(value: fraction, color: color, minHeight: 10),
            ),
          ),
          const SizedBox(width: AppSpacing.sm),
          SizedBox(
            width: 44,
            child: Text('${hours.toStringAsFixed(1)}h',
                style: theme.textTheme.labelLarge, textAlign: TextAlign.right),
          ),
        ],
      ),
    );
  }
}

/// Accumulated sleep debt/surplus over the last N nights — your own rolling
/// average as "need" vs. what you actually got, not a generic 8-hour rule.
class _SleepDebtCard extends StatelessWidget {
  final SleepDebtResponse debt;
  const _SleepDebtCard({required this.debt});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    final need = debt.neededHoursPerNight;
    final accumulated = debt.accumulatedHours;

    if (need == null || accumulated == null) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Row(
            children: [
              Icon(Icons.hourglass_empty, size: 20, color: theme.colorScheme.onSurfaceVariant),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Text(
                  debt.limitations.isNotEmpty
                      ? debt.limitations.first
                      : 'Not enough sleep history yet for a personal need estimate.',
                  style: theme.textTheme.bodySmall,
                ),
              ),
            ],
          ),
        ),
      );
    }

    final isDebt = accumulated > 0;
    final color = isDebt ? status.poor : status.good;
    final bg = isDebt ? status.poorContainer : status.goodContainer;
    final onBg = isDebt ? status.onPoorContainer : status.onGoodContainer;

    return Card(
      color: bg,
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(isDebt ? Icons.trending_down : Icons.trending_up, color: color),
                const SizedBox(width: AppSpacing.sm),
                Text(
                  isDebt
                      ? '${accumulated.abs().toStringAsFixed(1)}h sleep debt'
                      : '${accumulated.abs().toStringAsFixed(1)}h sleep surplus',
                  style: theme.textTheme.titleMedium?.copyWith(color: onBg),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.xs),
            Text(
              'Your personal need is ~${need.toStringAsFixed(1)}h/night (your own rolling average, '
              'not a generic target) — accumulated over ${debt.nightsConsidered} of the last '
              '${debt.windowDays} nights.',
              style: theme.textTheme.bodySmall?.copyWith(color: onBg),
            ),
          ],
        ),
      ),
    );
  }
}

/// How regular bed/wake times have been — distinct from duration/debt above,
/// this is about *when*, not *how much*.
class _SleepConsistencyCard extends StatelessWidget {
  final SleepConsistencyResponse consistency;
  const _SleepConsistencyCard({required this.consistency});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    final score = consistency.consistencyScore!;
    final color = status.forScore(score, goodAt: 75, fairAt: 50);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Row(
          children: [
            Icon(Icons.schedule_outlined, size: 28, color: color),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Text('Sleep consistency', style: theme.textTheme.titleMedium),
                      const Spacer(),
                      Text('$score', style: theme.textTheme.titleLarge?.copyWith(color: color)),
                    ],
                  ),
                  const SizedBox(height: AppSpacing.xs),
                  Text(
                    'Avg bedtime ${consistency.avgBedtime} · avg wake ${consistency.avgWakeTime} '
                    'over ${consistency.nightsConsidered} nights',
                    style: theme.textTheme.bodySmall,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SleepTrendsCard extends StatelessWidget {
  final List<SleepTrendPoint> trends;
  final int windowDays;
  final bool loading;
  final ValueChanged<int> onWindowChanged;

  const _SleepTrendsCard({
    required this.trends,
    required this.windowDays,
    required this.loading,
    required this.onWindowChanged,
  });

  static const List<int> _windowOptions = [7, 30, 90];
  // A per-night row list is only readable up to a couple weeks — beyond
  // that, the average/trend summary above carries the real signal and the
  // list becomes a recent-nights detail view, not the whole window.
  static const int _maxRowsShown = 14;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final shown = trends.length > _maxRowsShown
        ? trends.sublist(trends.length - _maxRowsShown)
        : trends;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text('Trend', style: theme.textTheme.titleMedium),
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
              Text('No nights in this window yet.', style: theme.textTheme.bodySmall)
            else ...[
              if (windowDays > 7) ...[
                _TrendSummaryRow(trends: trends, windowDays: windowDays),
                const SizedBox(height: AppSpacing.md),
                if (trends.length > _maxRowsShown)
                  Padding(
                    padding: const EdgeInsets.only(bottom: AppSpacing.sm),
                    child: Text(
                      'Most recent $_maxRowsShown of ${trends.length} nights:',
                      style: theme.textTheme.bodySmall,
                    ),
                  ),
              ],
              ...shown.map((t) => _TrendRow(point: t)),
            ],
          ],
        ),
      ),
    );
  }
}

class _TrendSummaryRow extends StatelessWidget {
  final List<SleepTrendPoint> trends;
  final int windowDays;
  const _TrendSummaryRow({required this.trends, required this.windowDays});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final avgHours = trends.map((t) => t.totalHours).reduce((a, b) => a + b) / trends.length;
    final avgScore = trends.map((t) => t.sleepScore).reduce((a, b) => a + b) / trends.length;

    // First half vs second half of the window — a simple, honest trend
    // direction without pretending to statistical significance testing.
    String? trendLabel;
    if (trends.length >= 6) {
      final mid = trends.length ~/ 2;
      final firstHalfAvg = trends.sublist(0, mid).map((t) => t.sleepScore).reduce((a, b) => a + b) / mid;
      final secondHalfAvg =
          trends.sublist(mid).map((t) => t.sleepScore).reduce((a, b) => a + b) / (trends.length - mid);
      final delta = secondHalfAvg - firstHalfAvg;
      if (delta.abs() >= 3) {
        trendLabel = delta > 0 ? 'improving' : 'declining';
      } else {
        trendLabel = 'steady';
      }
    }

    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(
              '${avgHours.toStringAsFixed(1)}h avg · ${avgScore.round()} avg score over ${trends.length} nights',
              style: theme.textTheme.bodyMedium,
            ),
          ),
          if (trendLabel != null)
            Text(trendLabel, style: theme.textTheme.labelLarge?.copyWith(color: theme.colorScheme.primary)),
        ],
      ),
    );
  }
}

class _TrendRow extends StatelessWidget {
  final SleepTrendPoint point;
  const _TrendRow({required this.point});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = theme.status.forScore(point.sleepScore, goodAt: 80, fairAt: 60);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
      child: Row(
        children: [
          SizedBox(
            width: 72,
            child: Text(point.date.substring(5), style: theme.textTheme.bodySmall),
          ),
          Expanded(
            child: Text('${point.totalHours.toStringAsFixed(1)}h', style: theme.textTheme.bodyMedium),
          ),
          SizedBox(
            width: 40,
            child: Text('${point.sleepScore}',
                style: theme.textTheme.labelLarge?.copyWith(color: color), textAlign: TextAlign.right),
          ),
        ],
      ),
    );
  }
}
