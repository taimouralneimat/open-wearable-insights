import 'package:flutter/material.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../widgets/state_views.dart';

/// Training-load (ACWR / "Strain") detail view — closes parity-matrix row 2.
/// Backed by the real GET /api/v1/trainingload/summary and /trends
/// endpoints (trainingload-v1, HR-zone-weighted, see ADR-0006 follow-up).
class TrainingLoadPage extends StatefulWidget {
  const TrainingLoadPage({super.key});

  @override
  State<TrainingLoadPage> createState() => _TrainingLoadPageState();
}

class _TrainingLoadPageState extends State<TrainingLoadPage> {
  final _apiClient = ApiClient();
  TrainingLoadSummaryResponse? _summary;
  List<TrainingLoadTrendPointResponse> _trends = [];
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
      final summary = await _apiClient.getTrainingLoadSummary();
      final trends = await _apiClient.getTrainingLoadTrends();
      if (!mounted) return;
      setState(() {
        _summary = summary;
        _trends = trends;
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
        title: const Text('Training load'),
        actions: [IconButton(icon: const Icon(Icons.refresh), tooltip: 'Refresh', onPressed: _load)],
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading) return const LoadingView(label: 'Loading training load…');
    if (_error != null) return ErrorView(message: _error!, onRetry: _load);
    final summary = _summary;
    if (summary == null) return const EmptyView(icon: Icons.speed_outlined, title: 'No data');

    final theme = Theme.of(context);
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: [
          _AcwrCard(summary: summary),
          const SizedBox(height: AppSpacing.lg),
          Container(
            padding: const EdgeInsets.all(AppSpacing.md),
            decoration: BoxDecoration(
              color: theme.colorScheme.surfaceContainerHighest,
              borderRadius: BorderRadius.circular(AppRadius.md),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: summary.limitations
                  .map((l) => Padding(
                        padding: const EdgeInsets.symmetric(vertical: 2),
                        child: Text(l, style: theme.textTheme.bodySmall),
                      ))
                  .toList(),
            ),
          ),
          if (_trends.isNotEmpty) ...[
            const SizedBox(height: AppSpacing.lg),
            _TrendCard(trends: _trends),
          ],
        ],
      ),
    );
  }
}

class _AcwrCard extends StatelessWidget {
  final TrainingLoadSummaryResponse summary;
  const _AcwrCard({required this.summary});

  Color _statusColor(BuildContext context) {
    final status = Theme.of(context).status;
    switch (summary.loadStatus) {
      case 'low':
        return status.info;
      case 'optimal':
        return status.good;
      case 'elevated':
        return status.fair;
      case 'high':
        return status.poor;
      default:
        return Theme.of(context).colorScheme.onSurfaceVariant;
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = _statusColor(context);
    final acwr = summary.acwr;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          children: [
            Text('ACWR', style: theme.textTheme.labelSmall),
            const SizedBox(height: AppSpacing.sm),
            Text(
              acwr != null ? acwr.toStringAsFixed(2) : '—',
              style: theme.textTheme.displayLarge?.copyWith(color: color, fontSize: 48),
            ),
            const SizedBox(height: AppSpacing.sm),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md, vertical: AppSpacing.xs),
              decoration: BoxDecoration(color: color.withValues(alpha: 0.15), borderRadius: BorderRadius.circular(AppRadius.pill)),
              child: Text(
                summary.loadStatus.toUpperCase(),
                style: theme.textTheme.labelMedium?.copyWith(color: color, fontWeight: FontWeight.w700),
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            const Divider(height: 1),
            const SizedBox(height: AppSpacing.lg),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                _Stat(label: 'Acute (7d avg)', value: summary.acuteLoad?.toStringAsFixed(0) ?? '—'),
                _Stat(label: 'Chronic (28d avg)', value: summary.chronicLoad?.toStringAsFixed(0) ?? '—'),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  final String label;
  final String value;
  const _Stat({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      children: [
        Text(value, style: theme.textTheme.headlineSmall),
        const SizedBox(height: AppSpacing.xs),
        Text(label, style: theme.textTheme.bodySmall),
      ],
    );
  }
}

class _TrendCard extends StatelessWidget {
  final List<TrainingLoadTrendPointResponse> trends;
  const _TrendCard({required this.trends});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final maxLoad = trends.map((t) => t.load).reduce((a, b) => a > b ? a : b);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Daily load (active days only)', style: theme.textTheme.titleMedium),
            const SizedBox(height: AppSpacing.md),
            ...trends.map((t) {
              final fraction = maxLoad > 0 ? (t.load / maxLoad).clamp(0.0, 1.0) : 0.0;
              return Padding(
                padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
                child: Row(
                  children: [
                    SizedBox(width: 60, child: Text(t.date.substring(5), style: theme.textTheme.bodySmall)),
                    const SizedBox(width: AppSpacing.sm),
                    Expanded(
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(AppRadius.pill),
                        child: LinearProgressIndicator(value: fraction, minHeight: 10),
                      ),
                    ),
                    const SizedBox(width: AppSpacing.sm),
                    SizedBox(width: 44, child: Text(t.load.toStringAsFixed(0), style: theme.textTheme.labelLarge, textAlign: TextAlign.right)),
                  ],
                ),
              );
            }),
          ],
        ),
      ),
    );
  }
}
