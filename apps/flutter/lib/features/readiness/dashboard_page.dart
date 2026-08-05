import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../app/app.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../widgets/state_views.dart';

/// Dashboard page — shows readiness score, factor contributions, and daily coach.
///
/// Handles all states: empty, loading, calibration, stale, partial, error, populated.
class DashboardPage extends ConsumerStatefulWidget {
  const DashboardPage({super.key});

  @override
  ConsumerState<DashboardPage> createState() => _DashboardPageState();
}

class _DashboardPageState extends ConsumerState<DashboardPage> {
  final _apiClient = ApiClient();
  ReadinessResponse? _readiness;
  ScoreDiffResponse? _scoreDiff;
  InsightResponse? _insight;
  HabitCueResponse? _habitCue;
  GarminEnrichmentResponse? _garminEnrichment;
  String? _displayName;
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
      final readiness = await _apiClient.getLatestReadiness();
      final insight = await _apiClient.getInsight();
      ScoreDiffResponse? diff;
      try { diff = await _apiClient.getScoreDiff(); } catch (_) { diff = null; }
      // Best-effort — a missing/failed profile or cue fetch shouldn't block
      // the dashboard from loading, they're secondary surfaces.
      String? name;
      try { name = (await _apiClient.getProfile()).displayName; } catch (_) { name = null; }
      HabitCueResponse? cue;
      try { cue = await _apiClient.getHabitCue(); } catch (_) { cue = null; }
      // Best-effort — Garmin-exclusive enrichment isn't available until a
      // Garmin Connect sync has run at least once; a missing/failed fetch
      // just means those cards don't show, not a dashboard-wide error.
      GarminEnrichmentResponse? enrichment;
      try { enrichment = await _apiClient.getGarminEnrichmentToday(); } catch (_) { enrichment = null; }
      setState(() {
        _readiness = readiness;
        _insight = insight;
        _scoreDiff = diff;
        _displayName = name;
        _habitCue = cue;
        _garminEnrichment = enrichment;
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
    final title = (_displayName != null && _displayName!.isNotEmpty) ? 'Hi, $_displayName' : 'Today';
    return Scaffold(
      appBar: AppBar(
        title: Text(title),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: _loadData,
          ),
          const MoreMenuButton(),
          const SizedBox(width: AppSpacing.xs),
        ],
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading) return const LoadingView(label: 'Loading readiness…');
    if (_error != null) return ErrorView(title: "Couldn't load dashboard", message: _error!, onRetry: _loadData);
    if (_readiness == null) {
      return EmptyView(
        icon: Icons.monitor_heart_outlined,
        title: 'No data yet',
        message: 'Import wearable data to see your readiness score.',
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
    final r = _readiness!;
    final i = _insight;
    return RefreshIndicator(
      onRefresh: _loadData,
      child: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: [
          _ReadinessCard(readiness: r),
          if (_scoreDiff != null) ...[
            const SizedBox(height: AppSpacing.lg),
            _ScoreDiffCard(diff: _scoreDiff!),
          ],
          if (_garminEnrichment != null &&
              (_garminEnrichment!.hasBodyBattery || _garminEnrichment!.hasTrainingReadiness)) ...[
            const SizedBox(height: AppSpacing.lg),
            _GarminEnrichmentCard(enrichment: _garminEnrichment!),
          ],
          const SizedBox(height: AppSpacing.lg),
          if (i != null) ...[
            _CoachCard(insight: i),
            const SizedBox(height: AppSpacing.lg),
          ],
          _WorkoutGeneratorEntryCard(),
          const SizedBox(height: AppSpacing.lg),
          if (_habitCue != null && _habitCue!.present) ...[
            _HabitCueCard(cue: _habitCue!),
            const SizedBox(height: AppSpacing.lg),
          ],
          _FactorsCard(readiness: r),
          const SizedBox(height: AppSpacing.lg),
          _HealthspanEntryCard(),
          const SizedBox(height: AppSpacing.lg),
          _MonthlyReportEntryCard(),
          const SizedBox(height: AppSpacing.lg),
          _DataQualityCard(readiness: r),
        ],
      ),
    );
  }
}

/// A single, specific habit suggestion triggered by today's worst real
/// readiness factor — the cue in a cue/response/reward loop, except the cue
/// is a physiological signal from the wearable, not a fixed time or place.
class _HabitCueCard extends StatelessWidget {
  final HabitCueResponse cue;
  const _HabitCueCard({required this.cue});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      color: theme.colorScheme.tertiaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(Icons.bolt_outlined, color: theme.colorScheme.onTertiaryContainer),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    cue.suggestedBehavior ?? 'Try something today',
                    style: theme.textTheme.titleMedium?.copyWith(color: theme.colorScheme.onTertiaryContainer),
                  ),
                  const SizedBox(height: AppSpacing.xs),
                  Text(
                    cue.reasoning,
                    style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onTertiaryContainer),
                  ),
                  const SizedBox(height: AppSpacing.sm),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: TextButton(
                      onPressed: () => context.go('/journal'),
                      child: const Text('Log it'),
                    ),
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

class _ScoreDiffCard extends StatelessWidget {
  final ScoreDiffResponse diff;
  const _ScoreDiffCard({required this.diff});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    final deltaColor = diff.scoreDelta > 0
        ? status.good
        : diff.scoreDelta < 0
            ? status.poor
            : theme.colorScheme.onSurfaceVariant;
    final deltaIcon = diff.scoreDelta > 0
        ? Icons.trending_up
        : diff.scoreDelta < 0
            ? Icons.trending_down
            : Icons.trending_flat;
    final deltaText = diff.scoreDelta > 0 ? '+${diff.scoreDelta}' : '${diff.scoreDelta}';

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.compare_arrows, size: 20, color: theme.colorScheme.onSurfaceVariant),
                const SizedBox(width: AppSpacing.sm),
                Text(diff.hasPriorData ? 'vs Yesterday' : 'Day-over-day', style: theme.textTheme.titleMedium),
                const Spacer(),
                if (diff.hasPriorData) ...[
                  Icon(deltaIcon, color: deltaColor, size: 20),
                  const SizedBox(width: AppSpacing.xs),
                  Text(deltaText, style: theme.textTheme.titleMedium?.copyWith(color: deltaColor)),
                ],
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(diff.summary, style: theme.textTheme.bodyMedium),
            const SizedBox(height: AppSpacing.md),
            ...diff.factorDiffs.where((f) => f.direction != 'unchanged').map((f) => _FactorDiffRow(diff: f)),
          ],
        ),
      ),
    );
  }
}

/// Entry point into the deterministic workout generator (parity-matrix row
/// 26) — a real form (goal/equipment/limitations/duration), not a fetched
/// summary, so unlike _HealthspanEntryCard/_MonthlyReportEntryCard below it
/// has nothing to load up front and is just a static navigation card. Sits
/// directly under the coach card since the parity-matrix entry itself calls
/// this "a natural fit for the existing local-LLM coach module" — this is
/// the deterministic-template half of that capability (see
/// WorkoutGeneratorService's class Javadoc for why the LLM half is
/// deliberately deferred).
class _WorkoutGeneratorEntryCard extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: ListTile(
        leading: Icon(Icons.fitness_center, color: theme.colorScheme.primary),
        title: const Text('Workout generator'),
        subtitle: const Text('Generate a workout from your goal, equipment, and limitations'),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => context.push('/workout-generator'),
      ),
    );
  }
}

/// Lightweight entry point into the full Healthspan view (parity-matrix
/// row 24) — fetches its own summary so it can show a live at-a-glance
/// value without coupling to the parent page's load sequence (same pattern
/// as activities_page.dart's entry cards for VO2max/Strength/Training load).
/// Lives on the dashboard rather than the Activities page because this is a
/// composite score spanning fitness, strength, sleep, and training data —
/// the same multi-domain scope as the readiness score above it, not a
/// single-domain activity metric.
class _HealthspanEntryCard extends StatefulWidget {
  @override
  State<_HealthspanEntryCard> createState() => _HealthspanEntryCardState();
}

class _HealthspanEntryCardState extends State<_HealthspanEntryCard> {
  final _apiClient = ApiClient();
  HealthspanSummaryResponse? _summary;

  @override
  void initState() {
    super.initState();
    _apiClient.getHealthspanSummary().then((s) {
      if (mounted) setState(() => _summary = s);
    }).catchError((_) {});
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final summary = _summary;
    final score = summary?.thirtyDay.score;
    return Card(
      child: ListTile(
        leading: Icon(Icons.favorite_border, color: theme.colorScheme.primary),
        title: const Text('Healthspan'),
        subtitle: Text(
          summary != null
              ? (score != null
                  ? '$score/100 · ${summary.thirtyDay.confidence} confidence'
                  : 'Not enough real history yet')
              : 'Loading…',
        ),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => context.push('/healthspan'),
      ),
    );
  }
}

/// Lightweight entry point into the full monthly performance report
/// (parity-matrix row 30) — fetches its own report so it can show a live
/// at-a-glance status without coupling to the parent page's load sequence
/// (same pattern as _HealthspanEntryCard above). Lives on the dashboard
/// rather than a single-domain page because, like Healthspan, this report
/// spans strain/sleep/recovery — multiple domains, not one.
class _MonthlyReportEntryCard extends StatefulWidget {
  @override
  State<_MonthlyReportEntryCard> createState() => _MonthlyReportEntryCardState();
}

class _MonthlyReportEntryCardState extends State<_MonthlyReportEntryCard> {
  final _apiClient = ApiClient();
  MonthlyReportResponse? _report;

  @override
  void initState() {
    super.initState();
    _apiClient.getMonthlyReport().then((r) {
      if (mounted) setState(() => _report = r);
    }).catchError((_) {});
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final report = _report;
    return Card(
      child: ListTile(
        leading: Icon(Icons.calendar_month_outlined, color: theme.colorScheme.primary),
        title: const Text('Monthly report'),
        subtitle: Text(
          report != null
              ? (report.sufficientHistory
                  ? '${report.month} · ${report.overallConfidence} confidence'
                  : '${report.recoveryScoreCount}/${report.requiredRecoveryScoreCount} recovery scores so far')
              : 'Loading…',
        ),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => context.push('/monthly-report'),
      ),
    );
  }
}

/// Garmin-exclusive metrics — Body Battery and Garmin's own proprietary
/// Training Readiness score. Deliberately not blended into this app's own
/// readiness score above; shown here so Garmin's opinion and this app's
/// computed opinion stay visibly distinct rather than merged into one
/// number. "As of" dates are shown because these are daily aggregates that
/// lag behind real time — see GarminEnrichmentService.
class _GarminEnrichmentCard extends StatelessWidget {
  final GarminEnrichmentResponse enrichment;
  const _GarminEnrichmentCard({required this.enrichment});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.watch_outlined, size: 20, color: theme.colorScheme.onSurfaceVariant),
                const SizedBox(width: AppSpacing.sm),
                Text('From your Garmin', style: theme.textTheme.titleMedium),
              ],
            ),
            const SizedBox(height: AppSpacing.md),
            if (enrichment.hasBodyBattery) ...[
              Row(
                children: [
                  Icon(Icons.battery_charging_full, size: 18, color: status.good),
                  const SizedBox(width: AppSpacing.sm),
                  Expanded(child: Text('Body Battery', style: theme.textTheme.bodyMedium)),
                  Text(
                    '${enrichment.bodyBatteryLow?.toInt() ?? '–'} – ${enrichment.bodyBatteryHigh?.toInt() ?? '–'}',
                    style: theme.textTheme.titleMedium,
                  ),
                ],
              ),
              if (enrichment.bodyBatteryCharged != null || enrichment.bodyBatteryDrained != null) ...[
                const SizedBox(height: AppSpacing.xs),
                Text(
                  'Charged +${enrichment.bodyBatteryCharged?.toInt() ?? 0} · Drained -${enrichment.bodyBatteryDrained?.toInt() ?? 0}'
                  '${enrichment.bodyBatteryAsOf != null ? ' · as of ${enrichment.bodyBatteryAsOf}' : ''}',
                  style: theme.textTheme.bodySmall,
                ),
              ],
              if (enrichment.hasTrainingReadiness) const SizedBox(height: AppSpacing.md),
            ],
            if (enrichment.hasTrainingReadiness) ...[
              Row(
                children: [
                  Icon(Icons.speed_outlined, size: 18, color: theme.colorScheme.primary),
                  const SizedBox(width: AppSpacing.sm),
                  Expanded(child: Text('Garmin Training Readiness', style: theme.textTheme.bodyMedium)),
                  Text('${enrichment.garminTrainingReadiness}', style: theme.textTheme.titleMedium),
                ],
              ),
              const SizedBox(height: AppSpacing.xs),
              Text(
                'Garmin\'s own recovery estimate'
                '${enrichment.trainingReadinessAsOf != null ? ' · ${enrichment.trainingReadinessAsOf}' : ''}',
                style: theme.textTheme.bodySmall,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _FactorDiffRow extends StatelessWidget {
  final FactorDiffResponse diff;
  const _FactorDiffRow({required this.diff});

  @override
  Widget build(BuildContext context) {
    final status = Theme.of(context).status;
    final color = diff.direction == 'improved' ? status.good : status.poor;
    final icon = diff.direction == 'improved' ? Icons.arrow_upward : Icons.arrow_downward;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        children: [
          Icon(icon, color: color, size: 14),
          const SizedBox(width: AppSpacing.sm),
          Expanded(child: Text(diff.name, style: Theme.of(context).textTheme.bodySmall)),
          Text(
            diff.contributionDelta > 0 ? '+${diff.contributionDelta.toStringAsFixed(1)}' : diff.contributionDelta.toStringAsFixed(1),
            style: Theme.of(context).textTheme.labelMedium?.copyWith(color: color),
          ),
        ],
      ),
    );
  }
}

class _ReadinessCard extends StatelessWidget {
  final ReadinessResponse readiness;
  const _ReadinessCard({required this.readiness});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    final color = status.forScore(readiness.score);
    final container = status.containerForScore(readiness.score);
    final band = readiness.score >= 75 ? 'Good' : readiness.score >= 50 ? 'Fair' : 'Needs attention';

    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.xl, horizontal: AppSpacing.lg),
        child: Column(
          children: [
            Text('READINESS', style: theme.textTheme.labelSmall),
            const SizedBox(height: AppSpacing.sm),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                Text('${readiness.score}', style: theme.textTheme.displayLarge?.copyWith(color: color)),
                Text('/100', style: theme.textTheme.titleMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
              ],
            ),
            const SizedBox(height: AppSpacing.md),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md, vertical: AppSpacing.xs),
              decoration: BoxDecoration(color: container, borderRadius: BorderRadius.circular(AppRadius.pill)),
              child: Text(
                readiness.provisional ? '$band · provisional' : band,
                style: theme.textTheme.labelLarge?.copyWith(color: color),
              ),
            ),
            const SizedBox(height: AppSpacing.md),
            Text(
              'Algorithm v${readiness.algorithmVersion} · ${readiness.baselinePeriod}',
              style: theme.textTheme.bodySmall,
            ),
            const SizedBox(height: AppSpacing.md),
            Text(readiness.explanation, textAlign: TextAlign.center, style: theme.textTheme.bodyLarge),
          ],
        ),
      ),
    );
  }
}

class _CoachCard extends StatefulWidget {
  final InsightResponse insight;
  const _CoachCard({required this.insight});

  @override
  State<_CoachCard> createState() => _CoachCardState();
}

class _CoachCardState extends State<_CoachCard> {
  final _apiClient = ApiClient();
  WhyAnswerResponse? _why;
  bool _whyLoading = false;
  String? _whyError;
  bool _expanded = false;

  Future<void> _askWhy() async {
    setState(() {
      _expanded = true;
      _whyLoading = true;
      _whyError = null;
    });
    try {
      final why = await _apiClient.getWhyAnswer();
      setState(() {
        _why = why;
        _whyLoading = false;
      });
    } catch (e) {
      setState(() {
        _whyError = e.toString();
        _whyLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    final insight = widget.insight;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(Icons.lightbulb_outline, size: 20, color: theme.colorScheme.tertiary),
                const SizedBox(width: AppSpacing.sm),
                Expanded(child: Text(insight.headline, style: theme.textTheme.titleMedium)),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(insight.summary, style: theme.textTheme.bodyMedium),
            const SizedBox(height: AppSpacing.md),
            Text('RECOMMENDED ACTIONS', style: theme.textTheme.labelSmall),
            const SizedBox(height: AppSpacing.xs),
            ...insight.recommendedActions.map((a) => _BulletLine(text: a)),
            if (insight.cautions.isNotEmpty) ...[
              const SizedBox(height: AppSpacing.md),
              Text('CAUTIONS', style: theme.textTheme.labelSmall?.copyWith(color: status.fair)),
              const SizedBox(height: AppSpacing.xs),
              ...insight.cautions.map((c) => _BulletLine(text: c, color: status.fair)),
            ],
            const SizedBox(height: AppSpacing.sm),
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton.icon(
                onPressed: _expanded ? null : _askWhy,
                icon: const Icon(Icons.help_outline, size: 16),
                label: const Text('Why?'),
              ),
            ),
            if (_expanded) ...[
              const Divider(height: AppSpacing.xl),
              if (_whyLoading) const Center(child: CircularProgressIndicator()),
              if (_whyError != null)
                Text('Could not load explanation: $_whyError',
                    style: theme.textTheme.bodySmall?.copyWith(color: status.poor)),
              if (_why != null) _WhyAnswerSection(why: _why!),
            ],
          ],
        ),
      ),
    );
  }
}

class _BulletLine extends StatelessWidget {
  final String text;
  final Color? color;
  const _BulletLine({required this.text, this.color});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('–  ', style: theme.textTheme.bodyMedium?.copyWith(color: color)),
          Expanded(child: Text(text, style: theme.textTheme.bodyMedium?.copyWith(color: color))),
        ],
      ),
    );
  }
}

class _WhyAnswerSection extends StatelessWidget {
  final WhyAnswerResponse why;
  const _WhyAnswerSection({required this.why});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(why.answer, style: theme.textTheme.bodyMedium?.copyWith(fontStyle: FontStyle.italic)),
        const SizedBox(height: AppSpacing.md),
        Text('CITED METRICS', style: theme.textTheme.labelSmall),
        const SizedBox(height: AppSpacing.xs),
        ...why.citedMetrics.map((m) {
          final color = m.direction == 'positive'
              ? status.good
              : m.direction == 'negative'
                  ? status.poor
                  : theme.colorScheme.onSurfaceVariant;
          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 2),
            child: Row(
              children: [
                Expanded(child: Text('${m.name}: ${m.value}', style: theme.textTheme.bodyMedium)),
                Text(m.contribution, style: theme.textTheme.labelMedium?.copyWith(color: color)),
              ],
            ),
          );
        }),
      ],
    );
  }
}

class _FactorsCard extends StatelessWidget {
  final ReadinessResponse readiness;
  const _FactorsCard({required this.readiness});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Factor contributions', style: theme.textTheme.titleMedium),
            const SizedBox(height: AppSpacing.md),
            ...readiness.factors.map((f) => _FactorRow(factor: f)),
            const SizedBox(height: AppSpacing.sm),
            Text(readiness.missingDataTreatment, style: theme.textTheme.bodySmall),
          ],
        ),
      ),
    );
  }
}

class _FactorRow extends StatelessWidget {
  final FactorResponse factor;
  const _FactorRow({required this.factor});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    final icon = factor.direction == 'positive'
        ? Icons.arrow_upward
        : factor.direction == 'negative'
            ? Icons.arrow_downward
            : Icons.remove;
    final color = factor.direction == 'positive'
        ? status.good
        : factor.direction == 'negative'
            ? status.poor
            : theme.colorScheme.onSurfaceVariant;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
      child: Row(
        children: [
          Icon(icon, color: color, size: 16),
          const SizedBox(width: AppSpacing.sm),
          Expanded(child: Text(factor.name, style: theme.textTheme.bodyMedium)),
          Text(factor.contribution.toStringAsFixed(1), style: theme.textTheme.labelLarge?.copyWith(color: color)),
        ],
      ),
    );
  }
}

class _DataQualityCard extends StatelessWidget {
  final ReadinessResponse readiness;
  const _DataQualityCard({required this.readiness});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Data quality', style: theme.textTheme.titleMedium),
            const SizedBox(height: AppSpacing.sm),
            _QualityRow(label: 'Confidence', value: readiness.confidence),
            _QualityRow(label: 'Data quality', value: readiness.dataQuality),
            _QualityRow(label: 'Baseline', value: readiness.baselinePeriod),
            const SizedBox(height: AppSpacing.sm),
            Text('Limitations: ${readiness.limitations}', style: theme.textTheme.bodySmall),
          ],
        ),
      ),
    );
  }
}

class _QualityRow extends StatelessWidget {
  final String label;
  final String value;
  const _QualityRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        children: [
          Text(label, style: theme.textTheme.bodyMedium),
          const Spacer(),
          Text(value, style: theme.textTheme.labelLarge),
        ],
      ),
    );
  }
}
