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
  bool _loading = true;
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
      final trends = await _apiClient.getSleepTrends();
      // Best-effort — debt is a secondary surface, shouldn't block the
      // main sleep summary from loading.
      SleepDebtResponse? debt;
      try { debt = await _apiClient.getSleepDebt(); } catch (_) { debt = null; }
      SleepPlanResponse? plan;
      try { plan = await _apiClient.getSleepPlan(); } catch (_) { plan = null; }
      setState(() {
        _summary = summary;
        _trends = trends;
        _debt = debt;
        _plan = plan;
        _loading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
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
          if (_trends != null) _SleepTrendsCard(trends: _trends!),
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
                  ? '${summary.totalHours.toStringAsFixed(1)}h total sleep · need ~${summary.sleepNeedHours!.toStringAsFixed(1)}h'
                  : '${summary.totalHours.toStringAsFixed(1)}h total sleep',
              style: theme.textTheme.bodyLarge,
            ),
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
              '${debt.windowDays} nights with real data.',
              style: theme.textTheme.bodySmall?.copyWith(color: onBg),
            ),
          ],
        ),
      ),
    );
  }
}

class _SleepTrendsCard extends StatelessWidget {
  final List<SleepTrendPoint> trends;
  const _SleepTrendsCard({required this.trends});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('7-day trend', style: theme.textTheme.titleMedium),
            const SizedBox(height: AppSpacing.md),
            ...trends.map((t) => _TrendRow(point: t)),
          ],
        ),
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
