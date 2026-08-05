import 'package:flutter/material.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../widgets/state_views.dart';

/// Monthly performance report view — closes parity-matrix row 30. Backed by
/// the real GET /api/v1/monthly-report endpoint (monthlyreport-v1), this
/// app's own original strain/sleep/recovery breakdown for one calendar
/// month, built entirely from real readiness-score history, training load,
/// and sleep-score trend data already computed elsewhere in this app. Not a
/// reproduction of any vendor's proprietary monthly-report design or
/// scoring — see the methodology/limitations shown on this page.
///
/// Lives on the dashboard (alongside HealthspanPage) rather than a
/// Trends-specific surface: like Healthspan, this spans multiple domains
/// (strain, sleep, recovery) rather than belonging to one of them.
class MonthlyReportPage extends StatefulWidget {
  const MonthlyReportPage({super.key});

  @override
  State<MonthlyReportPage> createState() => _MonthlyReportPageState();
}

class _MonthlyReportPageState extends State<MonthlyReportPage> {
  final _apiClient = ApiClient();
  MonthlyReportResponse? _report;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final report = await _apiClient.getMonthlyReport();
      if (!mounted) return;
      setState(() {
        _report = report;
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
      appBar: AppBar(
        title: const Text('Monthly report'),
        actions: [IconButton(icon: const Icon(Icons.refresh), tooltip: 'Refresh', onPressed: _load)],
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading) return const LoadingView(label: 'Loading monthly report…');
    if (_error != null) return ErrorView(message: _error!, onRetry: _load);
    final report = _report;
    if (report == null) return const EmptyView(icon: Icons.calendar_month_outlined, title: 'No data');

    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: [
          _MonthHeaderCard(report: report),
          const SizedBox(height: AppSpacing.lg),
          if (!report.sufficientHistory)
            _InsufficientHistoryCard(report: report)
          else ...[
            if (report.recovery != null) ...[
              _SectionCard(section: report.recovery!, icon: Icons.favorite_border),
              const SizedBox(height: AppSpacing.lg),
            ],
            if (report.strain != null) ...[
              _SectionCard(section: report.strain!, icon: Icons.local_fire_department_outlined),
              const SizedBox(height: AppSpacing.lg),
            ],
            if (report.sleep != null) ...[
              _SectionCard(section: report.sleep!, icon: Icons.bedtime_outlined),
              const SizedBox(height: AppSpacing.lg),
            ],
            _MethodologyCard(report: report),
          ],
        ],
      ),
    );
  }
}

class _MonthHeaderCard extends StatelessWidget {
  final MonthlyReportResponse report;
  const _MonthHeaderCard({required this.report});

  Color _confidenceColor(BuildContext context) {
    final status = Theme.of(context).status;
    switch (report.overallConfidence) {
      case 'medium':
        return status.fair;
      case 'low':
      case 'none':
        return status.poor;
      default:
        return Theme.of(context).colorScheme.onSurfaceVariant;
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Row(
          children: [
            Icon(Icons.calendar_month_outlined, color: theme.colorScheme.primary),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(report.month, style: theme.textTheme.titleLarge),
                  Text(
                    '${report.recoveryScoreCount} of ${report.requiredRecoveryScoreCount} real recovery scores needed',
                    style: theme.textTheme.bodySmall,
                  ),
                ],
              ),
            ),
            if (report.sufficientHistory)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md, vertical: AppSpacing.xs),
                decoration: BoxDecoration(
                  color: _confidenceColor(context).withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(AppRadius.pill),
                ),
                child: Text(
                  report.overallConfidence.toUpperCase(),
                  style: theme.textTheme.labelMedium?.copyWith(
                    color: _confidenceColor(context),
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _InsufficientHistoryCard extends StatelessWidget {
  final MonthlyReportResponse report;
  const _InsufficientHistoryCard({required this.report});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          children: [
            Icon(Icons.hourglass_empty, size: 40, color: theme.colorScheme.onSurfaceVariant),
            const SizedBox(height: AppSpacing.md),
            Text('Not enough real history yet', style: theme.textTheme.titleMedium, textAlign: TextAlign.center),
            const SizedBox(height: AppSpacing.sm),
            Text(
              report.insufficientHistoryMessage ?? '',
              style: theme.textTheme.bodySmall,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: AppSpacing.md),
            LinearProgressIndicator(
              value: (report.recoveryScoreCount / report.requiredRecoveryScoreCount).clamp(0.0, 1.0),
              minHeight: 8,
            ),
          ],
        ),
      ),
    );
  }
}

class _SectionCard extends StatelessWidget {
  final MonthlyMetricSectionResponse section;
  final IconData icon;
  const _SectionCard({required this.section, required this.icon});

  IconData get _trendIcon => switch (section.trendDirection) {
        'IMPROVING' => Icons.trending_up,
        'DECLINING' => Icons.trending_down,
        'STEADY' => Icons.trending_flat,
        _ => Icons.remove,
      };

  Color _trendColor(BuildContext context) {
    final status = Theme.of(context).status;
    return switch (section.trendDirection) {
      'IMPROVING' => status.good,
      'DECLINING' => status.poor,
      _ => Theme.of(context).colorScheme.onSurfaceVariant,
    };
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final value = section.averageValue;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(icon, size: 20, color: theme.colorScheme.onSurfaceVariant),
                const SizedBox(width: AppSpacing.sm),
                Expanded(child: Text(section.name, style: theme.textTheme.titleMedium)),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: AppSpacing.sm, vertical: 2),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.surfaceContainerHighest,
                    borderRadius: BorderRadius.circular(AppRadius.pill),
                  ),
                  child: Text(
                    section.confidence.toUpperCase(),
                    style: theme.textTheme.labelSmall,
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.md),
            if (value == null)
              Text('No real data for this dimension this month.', style: theme.textTheme.bodyMedium)
            else ...[
              Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(value.toStringAsFixed(1), style: theme.textTheme.displayLarge?.copyWith(fontSize: 32)),
                  const SizedBox(width: AppSpacing.xs),
                  Padding(
                    padding: const EdgeInsets.only(bottom: 6),
                    child: Text(section.unit, style: theme.textTheme.bodySmall),
                  ),
                  const Spacer(),
                  Icon(_trendIcon, color: _trendColor(context), size: 20),
                  const SizedBox(width: 4),
                  Text(
                    section.trendDirection[0] + section.trendDirection.substring(1).toLowerCase(),
                    style: theme.textTheme.bodyMedium?.copyWith(color: _trendColor(context)),
                  ),
                ],
              ),
              const SizedBox(height: AppSpacing.xs),
              Text('${section.daysWithData} of ${section.daysInMonth} days have real data', style: theme.textTheme.bodySmall),
              const SizedBox(height: AppSpacing.sm),
              Text(section.trendDetail, style: theme.textTheme.bodySmall),
            ],
            if (section.limitations.isNotEmpty) ...[
              const SizedBox(height: AppSpacing.sm),
              const Divider(height: 1),
              const SizedBox(height: AppSpacing.sm),
              ...section.limitations.map((l) => Padding(
                    padding: const EdgeInsets.symmetric(vertical: 2),
                    child: Text('• $l', style: theme.textTheme.bodySmall),
                  )),
            ],
          ],
        ),
      ),
    );
  }
}

class _MethodologyCard extends StatelessWidget {
  final MonthlyReportResponse report;
  const _MethodologyCard({required this.report});

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
                Icon(Icons.science_outlined, size: 18, color: theme.colorScheme.onSurfaceVariant),
                const SizedBox(width: AppSpacing.sm),
                Text('Methodology', style: theme.textTheme.titleMedium),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(report.methodology, style: theme.textTheme.bodySmall),
            const SizedBox(height: AppSpacing.md),
            const Divider(height: 1),
            const SizedBox(height: AppSpacing.md),
            Text('Limitations', style: theme.textTheme.titleSmall),
            const SizedBox(height: AppSpacing.xs),
            ...report.limitations.map((l) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 2),
                  child: Text('• $l', style: theme.textTheme.bodySmall),
                )),
          ],
        ),
      ),
    );
  }
}
