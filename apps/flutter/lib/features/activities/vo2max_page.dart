import 'package:flutter/material.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../widgets/state_views.dart';

/// VO2 Max estimate view — closes parity-matrix row 18. Backed by the real
/// GET /api/v1/vo2max and /vo2max/trend endpoints (vo2max-v1), which apply
/// the published Uth-Sorensen-Overgaard-Pedersen non-exercise regression
/// ("Heart Rate Ratio Method", European Journal of Applied Physiology,
/// 2004) to the account's own real recorded heart-rate data. Not a
/// reproduction of any wearable vendor's proprietary formula, not a
/// lab-measured or medical VO2max — see the methodology/limitations shown
/// on this page.
class Vo2MaxPage extends StatefulWidget {
  const Vo2MaxPage({super.key});

  @override
  State<Vo2MaxPage> createState() => _Vo2MaxPageState();
}

class _Vo2MaxPageState extends State<Vo2MaxPage> {
  final _apiClient = ApiClient();
  Vo2MaxEstimateResponse? _estimate;
  List<Vo2MaxTrendPointResponse> _trend = [];
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
      final estimate = await _apiClient.getVo2MaxEstimate();
      final trend = await _apiClient.getVo2MaxTrend();
      if (!mounted) return;
      setState(() {
        _estimate = estimate;
        _trend = trend;
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
        title: const Text('VO2 Max'),
        actions: [IconButton(icon: const Icon(Icons.refresh), tooltip: 'Refresh', onPressed: _load)],
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading) return const LoadingView(label: 'Loading VO2 Max estimate…');
    if (_error != null) return ErrorView(message: _error!, onRetry: _load);
    final estimate = _estimate;
    if (estimate == null) return const EmptyView(icon: Icons.monitor_heart_outlined, title: 'No data');

    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: [
          _Vo2MaxCard(estimate: estimate),
          const SizedBox(height: AppSpacing.lg),
          _MethodologyCard(estimate: estimate),
          if (_trend.isNotEmpty) ...[
            const SizedBox(height: AppSpacing.lg),
            _TrendCard(trend: _trend),
          ],
        ],
      ),
    );
  }
}

class _Vo2MaxCard extends StatelessWidget {
  final Vo2MaxEstimateResponse estimate;
  const _Vo2MaxCard({required this.estimate});

  Color _confidenceColor(BuildContext context) {
    final status = Theme.of(context).status;
    switch (estimate.confidence) {
      case 'medium':
        return status.fair;
      case 'low':
        return status.poor;
      default:
        return Theme.of(context).colorScheme.onSurfaceVariant;
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = _confidenceColor(context);
    final vo2Max = estimate.vo2Max;

    if (vo2Max == null) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.xl),
          child: Column(
            children: [
              Icon(Icons.monitor_heart_outlined, size: 40, color: theme.colorScheme.onSurfaceVariant),
              const SizedBox(height: AppSpacing.md),
              Text(
                'Not enough real data yet',
                style: theme.textTheme.titleMedium,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: AppSpacing.sm),
              ...estimate.limitations.map((l) => Padding(
                    padding: const EdgeInsets.symmetric(vertical: 2),
                    child: Text(l, style: theme.textTheme.bodySmall, textAlign: TextAlign.center),
                  )),
            ],
          ),
        ),
      );
    }

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          children: [
            Text('VO2 MAX ESTIMATE', style: theme.textTheme.labelSmall),
            const SizedBox(height: AppSpacing.sm),
            Text(
              vo2Max.toStringAsFixed(1),
              style: theme.textTheme.displayLarge?.copyWith(fontSize: 48),
            ),
            Text('mL/kg/min', style: theme.textTheme.bodySmall),
            const SizedBox(height: AppSpacing.sm),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md, vertical: AppSpacing.xs),
              decoration: BoxDecoration(color: color.withValues(alpha: 0.15), borderRadius: BorderRadius.circular(AppRadius.pill)),
              child: Text(
                'CONFIDENCE: ${estimate.confidence.toUpperCase()}',
                style: theme.textTheme.labelMedium?.copyWith(color: color, fontWeight: FontWeight.w700),
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            const Divider(height: 1),
            const SizedBox(height: AppSpacing.lg),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                _Stat(label: 'Max HR used', value: estimate.hrMaxBpm != null ? '${estimate.hrMaxBpm} bpm' : '—'),
                _Stat(label: 'Resting HR used', value: estimate.hrRestBpm != null ? '${estimate.hrRestBpm!.round()} bpm' : '—'),
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

class _MethodologyCard extends StatelessWidget {
  final Vo2MaxEstimateResponse estimate;
  const _MethodologyCard({required this.estimate});

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
            Text(estimate.methodology, style: theme.textTheme.bodySmall),
            if (estimate.hrMaxSource != null) ...[
              const SizedBox(height: AppSpacing.md),
              Text(estimate.hrMaxSource!, style: theme.textTheme.bodySmall),
            ],
            if (estimate.hrRestSource != null) ...[
              const SizedBox(height: AppSpacing.xs),
              Text(estimate.hrRestSource!, style: theme.textTheme.bodySmall),
            ],
            const SizedBox(height: AppSpacing.md),
            const Divider(height: 1),
            const SizedBox(height: AppSpacing.md),
            Text('Limitations', style: theme.textTheme.titleSmall),
            const SizedBox(height: AppSpacing.xs),
            ...estimate.limitations.map((l) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 2),
                  child: Text('• $l', style: theme.textTheme.bodySmall),
                )),
          ],
        ),
      ),
    );
  }
}

class _TrendCard extends StatelessWidget {
  final List<Vo2MaxTrendPointResponse> trend;
  const _TrendCard({required this.trend});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final maxVo2 = trend.map((t) => t.vo2Max).reduce((a, b) => a > b ? a : b);
    final minVo2 = trend.map((t) => t.vo2Max).reduce((a, b) => a < b ? a : b);
    final range = (maxVo2 - minVo2).clamp(0.1, double.infinity);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Monthly trend', style: theme.textTheme.titleMedium),
            const SizedBox(height: AppSpacing.xs),
            Text(
              'Months without enough real activity or heart-rate data are omitted, not estimated.',
              style: theme.textTheme.bodySmall,
            ),
            const SizedBox(height: AppSpacing.md),
            ...trend.map((t) {
              final fraction = ((t.vo2Max - minVo2) / range).clamp(0.05, 1.0);
              return Padding(
                padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
                child: Row(
                  children: [
                    SizedBox(width: 60, child: Text(t.month, style: theme.textTheme.bodySmall)),
                    const SizedBox(width: AppSpacing.sm),
                    Expanded(
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(AppRadius.pill),
                        child: LinearProgressIndicator(value: fraction, minHeight: 10),
                      ),
                    ),
                    const SizedBox(width: AppSpacing.sm),
                    SizedBox(width: 44, child: Text(t.vo2Max.toStringAsFixed(1), style: theme.textTheme.labelLarge, textAlign: TextAlign.right)),
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
