import 'package:flutter/material.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../widgets/confidence_banner.dart';
import '../../widgets/state_views.dart';

/// Data-quality dashboard page — shows completeness, coverage, and issues.
class DataQualityPage extends StatefulWidget {
  const DataQualityPage({super.key});

  @override
  State<DataQualityPage> createState() => _DataQualityPageState();
}

class _DataQualityPageState extends State<DataQualityPage> {
  final _apiClient = ApiClient();
  DataQualitySummary? _summary;
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
      final summary = await _apiClient.getDataQualitySummary();
      setState(() {
        _summary = summary;
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
        title: const Text('Data quality'),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), tooltip: 'Refresh', onPressed: _loadData),
          const SizedBox(width: AppSpacing.xs),
        ],
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading) return const LoadingView(label: 'Loading data quality…');
    if (_error != null) return ErrorView(message: _error!, onRetry: _loadData);
    if (_summary == null) {
      return const EmptyView(icon: Icons.analytics_outlined, title: 'No data quality metrics');
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
          _OverallQualityCard(summary: s),
          const SizedBox(height: AppSpacing.lg),
          _MetricCoverageCard(summary: s),
          const SizedBox(height: AppSpacing.lg),
          if (s.issues.isNotEmpty) _IssuesCard(issues: s.issues),
        ],
      ),
    );
  }
}

class _OverallQualityCard extends StatelessWidget {
  final DataQualitySummary summary;
  const _OverallQualityCard({required this.summary});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final completenessPct = (summary.completeness * 100).round();
    final color = theme.status.forScore(completenessPct, goodAt: 80, fairAt: 60);
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.xl, horizontal: AppSpacing.lg),
        child: Column(
          children: [
            Text('OVERALL COMPLETENESS', style: theme.textTheme.labelSmall),
            const SizedBox(height: AppSpacing.sm),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                Text('$completenessPct', style: theme.textTheme.displayLarge?.copyWith(color: color)),
                Text('%', style: theme.textTheme.titleMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
              ],
            ),
            const SizedBox(height: AppSpacing.md),
            ClipRRect(
              borderRadius: BorderRadius.circular(AppRadius.pill),
              child: LinearProgressIndicator(value: summary.completeness, color: color, minHeight: 8),
            ),
            const SizedBox(height: AppSpacing.lg),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                _InfoChip(label: 'Freshness', value: summary.freshness),
                _InfoChip(label: 'Days', value: '${summary.daysOfData}'),
                _InfoChip(label: 'Sources', value: '${summary.totalSources}'),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _InfoChip extends StatelessWidget {
  final String label;
  final String value;
  const _InfoChip({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      children: [
        Text(value, style: theme.textTheme.titleMedium),
        Text(label, style: theme.textTheme.bodySmall),
      ],
    );
  }
}

class _MetricCoverageCard extends StatelessWidget {
  final DataQualitySummary summary;
  const _MetricCoverageCard({required this.summary});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Metric coverage', style: theme.textTheme.titleMedium),
            const SizedBox(height: AppSpacing.md),
            ...summary.metrics.map((m) => _MetricRow(metric: m)),
          ],
        ),
      ),
    );
  }
}

class _MetricRow extends StatelessWidget {
  final MetricQuality metric;
  const _MetricRow({required this.metric});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    final coveragePct = (metric.coverage * 100).round();
    final color = metric.quality == 'good'
        ? status.good
        : metric.quality == 'fair'
            ? status.fair
            : status.poor;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
      child: Row(
        children: [
          SizedBox(width: 84, child: Text(metric.metric, style: theme.textTheme.bodyMedium)),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: ClipRRect(
              borderRadius: BorderRadius.circular(AppRadius.pill),
              child: LinearProgressIndicator(value: metric.coverage, color: color, minHeight: 10),
            ),
          ),
          const SizedBox(width: AppSpacing.sm),
          SizedBox(
            width: 44,
            child: Text('$coveragePct%', style: theme.textTheme.labelLarge?.copyWith(color: color), textAlign: TextAlign.right),
          ),
        ],
      ),
    );
  }
}

class _IssuesCard extends StatelessWidget {
  final List<String> issues;
  const _IssuesCard({required this.issues});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    return Card(
      color: status.fairContainer,
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.warning_amber, color: status.onFairContainer),
                const SizedBox(width: AppSpacing.sm),
                Text('Data quality issues', style: theme.textTheme.titleMedium?.copyWith(color: status.onFairContainer)),
              ],
            ),
            const SizedBox(height: AppSpacing.md),
            ...issues.map((issue) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('•  ', style: theme.textTheme.bodyMedium?.copyWith(color: status.onFairContainer)),
                      Expanded(child: Text(issue, style: theme.textTheme.bodyMedium?.copyWith(color: status.onFairContainer))),
                    ],
                  ),
                )),
          ],
        ),
      ),
    );
  }
}
