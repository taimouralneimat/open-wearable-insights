import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../widgets/state_views.dart';

/// Trends overview — closes the second half of parity-matrix row 8's
/// documented gap: "no unified Trends view — still split across
/// Sleep/Activities pages". Trend data (sleep, steps, VO2max, strength
/// training) previously lived scattered across four separate pages a user
/// had to navigate to individually. This page is a single, honest,
/// at-a-glance summary of all four, reusing the exact same real endpoints
/// those pages already call (ApiClient.getSleepTrends/getActivityTrends/
/// getVo2MaxTrend/getStrengthTrends) — no new backend logic.
///
/// Deliberately NOT a duplicate of the detail pages: SleepPage,
/// ActivitiesPage, Vo2MaxPage, and StrengthTrendPage keep their full
/// charts/history/baseline overlays/methodology exactly as they are. Each
/// section here shows the real most-recent value plus an honest trend
/// direction and taps through to that page for full detail.
///
/// Reachable from the overflow "More" menu (see MoreMenuButton in
/// app.dart) via a top-level route, not the bottom nav — restructuring the
/// four primary tabs is a separate, more disruptive IA decision this pass
/// deliberately leaves alone.
class TrendsPage extends StatefulWidget {
  const TrendsPage({super.key});

  @override
  State<TrendsPage> createState() => _TrendsPageState();
}

class _TrendsPageState extends State<TrendsPage> {
  final _apiClient = ApiClient();

  SleepTrendResponse? _sleep;
  bool _sleepFailed = false;
  ActivityTrendResponse? _activity;
  bool _activityFailed = false;
  List<Vo2MaxTrendPointResponse>? _vo2max;
  bool _vo2maxFailed = false;
  StrengthActivityTrendResponse? _strength;
  bool _strengthFailed = false;

  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _loadAll();
  }

  /// Each of the four trend sources is fetched independently and
  /// best-effort, same discipline as the dashboard's own per-card fetches
  /// (see dashboard_page.dart's _loadData): one missing/failed source
  /// (e.g. no strength training logged yet) must never blank the rest of
  /// the overview.
  Future<void> _loadAll() async {
    setState(() {
      _loading = true;
      _sleepFailed = false;
      _activityFailed = false;
      _vo2maxFailed = false;
      _strengthFailed = false;
    });

    SleepTrendResponse? sleep;
    bool sleepFailed = false;
    try {
      // 30 days: enough real nights for a meaningful first-half/second-half
      // read without the page defaulting to the server's bare 7-day window.
      sleep = await _apiClient.getSleepTrends(days: 30);
    } catch (_) {
      sleepFailed = true;
    }

    ActivityTrendResponse? activity;
    bool activityFailed = false;
    try {
      activity = await _apiClient.getActivityTrends(days: 30);
    } catch (_) {
      activityFailed = true;
    }

    List<Vo2MaxTrendPointResponse>? vo2max;
    bool vo2maxFailed = false;
    try {
      vo2max = await _apiClient.getVo2MaxTrend();
    } catch (_) {
      vo2maxFailed = true;
    }

    StrengthActivityTrendResponse? strength;
    bool strengthFailed = false;
    try {
      // 'monthly' buckets, the closest match to the 30-day window used for
      // sleep/activity above.
      strength = await _apiClient.getStrengthTrends(window: 'monthly');
    } catch (_) {
      strengthFailed = true;
    }

    if (!mounted) return;
    setState(() {
      _sleep = sleep;
      _sleepFailed = sleepFailed;
      _activity = activity;
      _activityFailed = activityFailed;
      _vo2max = vo2max;
      _vo2maxFailed = vo2maxFailed;
      _strength = strength;
      _strengthFailed = strengthFailed;
      _loading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Trends'),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), tooltip: 'Refresh', onPressed: _loadAll),
          const SizedBox(width: AppSpacing.xs),
        ],
      ),
      body: _loading ? const LoadingView(label: 'Loading trends…') : _buildBody(context),
    );
  }

  Widget _buildBody(BuildContext context) {
    final theme = Theme.of(context);
    final sleepPoints = _sleep?.points ?? const [];
    final activityPoints = _activity?.points ?? const [];
    final vo2maxPoints = _vo2max ?? const [];
    final strengthPoints = _strength?.points ?? const [];

    return RefreshIndicator(
      onRefresh: _loadAll,
      child: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: [
          Text(
            'A quick look at every trend this app tracks. Tap any section for the '
            'full chart, history, and methodology.',
            style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
          const SizedBox(height: AppSpacing.lg),
          _TrendSectionCard(
            icon: Icons.bedtime_outlined,
            title: 'Sleep',
            valueLine: sleepPoints.isEmpty
                ? null
                : '${sleepPoints.last.totalHours.toStringAsFixed(1)}h · '
                    'score ${sleepPoints.last.sleepScore}'
                    '${sleepPoints.last.granularity != 'day' ? ' (${sleepPoints.last.granularity} avg, ${sleepPoints.last.date.substring(5)})' : ' (${sleepPoints.last.date.substring(5)})'}',
            direction: _directionFromValues(sleepPoints.map((p) => p.sleepScore.toDouble()).toList()),
            emptyMessage: _sleepFailed
                ? "Couldn't load sleep trend right now."
                : 'No sleep data yet — import or sync to see a trend.',
            onTap: () => context.push('/sleep'),
          ),
          const SizedBox(height: AppSpacing.md),
          _TrendSectionCard(
            icon: Icons.directions_run_outlined,
            title: 'Activity (steps)',
            valueLine: activityPoints.isEmpty
                ? null
                : '${activityPoints.last.steps} steps'
                    '${activityPoints.last.granularity != 'day' ? ' (${activityPoints.last.granularity} avg, ${activityPoints.last.date.substring(5)})' : ' (${activityPoints.last.date.substring(5)})'}',
            direction: _directionFromValues(activityPoints.map((p) => p.steps.toDouble()).toList()),
            emptyMessage: _activityFailed
                ? "Couldn't load activity trend right now."
                : 'No step data yet — import or sync to see a trend.',
            onTap: () => context.push('/activities'),
          ),
          const SizedBox(height: AppSpacing.md),
          _TrendSectionCard(
            icon: Icons.monitor_heart_outlined,
            title: 'VO2 Max',
            valueLine: vo2maxPoints.isEmpty
                ? null
                : '${vo2maxPoints.last.vo2Max.toStringAsFixed(1)} mL/kg/min (${vo2maxPoints.last.month})',
            direction: _directionFromValues(vo2maxPoints.map((p) => p.vo2Max).toList()),
            emptyMessage: _vo2maxFailed
                ? "Couldn't load VO2 Max trend right now."
                : 'No estimate yet — needs real activity + heart-rate data.',
            onTap: () => context.push('/activities/vo2max'),
          ),
          const SizedBox(height: AppSpacing.md),
          _TrendSectionCard(
            icon: Icons.fitness_center_outlined,
            title: 'Strength training',
            valueLine: strengthPoints.isEmpty
                ? null
                : '${strengthPoints.last.totalMinutes.toStringAsFixed(0)} min '
                    '(month of ${strengthPoints.last.periodStart.substring(5)})',
            direction: _directionFromValues(strengthPoints.map((p) => p.totalMinutes).toList()),
            emptyMessage: _strengthFailed
                ? "Couldn't load strength trend right now."
                : 'No strength training logged yet.',
            onTap: () => context.push('/activities/strength'),
          ),
        ],
      ),
    );
  }
}

/// Simple, honest trend direction shared across every section on this page:
/// average of the first half of the available window vs. the second half —
/// the same convention _TrendSummaryRow (sleep_page.dart) already uses,
/// generalized to a relative (percentage) delta rather than a fixed
/// absolute threshold, since the four metrics compared here span wildly
/// different scales (a 0-100 sleep score vs. thousands of steps vs.
/// ~30-60 mL/kg/min VO2max vs. minutes of strength training — no single
/// absolute number means the same thing across all of them). Never claims
/// statistical significance, just a plain-language read of which half was
/// higher. Requires at least 4 points; anything shorter isn't a meaningful
/// two-half comparison and returns null (no direction shown) rather than a
/// guess.
enum _Direction { up, down, steady }

_Direction? _directionFromValues(List<double> values) {
  if (values.length < 4) return null;
  final mid = values.length ~/ 2;
  final firstAvg = _average(values.sublist(0, mid));
  final secondAvg = _average(values.sublist(mid));
  if (firstAvg == 0) return null;
  final relativeDelta = (secondAvg - firstAvg) / firstAvg.abs();
  if (relativeDelta.abs() < 0.05) return _Direction.steady;
  return relativeDelta > 0 ? _Direction.up : _Direction.down;
}

double _average(List<double> values) => values.reduce((a, b) => a + b) / values.length;

String _directionLabel(_Direction direction) {
  switch (direction) {
    case _Direction.up:
      return 'trending up';
    case _Direction.down:
      return 'trending down';
    case _Direction.steady:
      return 'steady';
  }
}

/// One tappable overview row for a single trend type. [valueLine] is the
/// real most-recent value formatted for display, or null when there's
/// nothing real to show yet — in which case [emptyMessage] is shown instead
/// (never a fabricated value). Matches this app's Card-based layout
/// convention (see dashboard_page.dart's entry cards).
class _TrendSectionCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String? valueLine;
  final _Direction? direction;
  final String emptyMessage;
  final VoidCallback onTap;

  const _TrendSectionCard({
    required this.icon,
    required this.title,
    required this.valueLine,
    required this.direction,
    required this.emptyMessage,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(AppRadius.md),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Row(
            children: [
              Icon(icon, color: theme.colorScheme.primary),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: theme.textTheme.titleMedium),
                    const SizedBox(height: AppSpacing.xs),
                    Text(
                      valueLine ?? emptyMessage,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: valueLine != null ? null : theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              if (direction != null) ...[
                const SizedBox(width: AppSpacing.sm),
                Text(
                  _directionLabel(direction!),
                  style: theme.textTheme.labelLarge?.copyWith(color: theme.colorScheme.primary),
                ),
              ],
              const SizedBox(width: AppSpacing.xs),
              Icon(Icons.chevron_right, color: theme.colorScheme.onSurfaceVariant),
            ],
          ),
        ),
      ),
    );
  }
}
