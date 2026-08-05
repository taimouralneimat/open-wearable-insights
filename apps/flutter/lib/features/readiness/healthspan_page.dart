import 'package:flutter/material.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../widgets/state_views.dart';

/// Healthspan / composite wellness score view — closes parity-matrix row
/// 24. Backed by the real GET /api/v1/healthspan endpoint (healthspan-v1),
/// this app's own original composite built from real VO2max, strength,
/// resting-heart-rate, sleep-consistency, and training-load data already
/// computed elsewhere in this app. Not a reproduction of any vendor's
/// proprietary biological-age formula, not developed with or validated by
/// any external research institute — see the disclaimer and methodology
/// shown on this page.
class HealthspanPage extends StatefulWidget {
  const HealthspanPage({super.key});

  @override
  State<HealthspanPage> createState() => _HealthspanPageState();
}

class _HealthspanPageState extends State<HealthspanPage> {
  final _apiClient = ApiClient();
  HealthspanSummaryResponse? _summary;
  bool _sixMonth = false;
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
      final summary = await _apiClient.getHealthspanSummary();
      if (!mounted) return;
      setState(() {
        _summary = summary;
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
        title: const Text('Healthspan'),
        actions: [IconButton(icon: const Icon(Icons.refresh), tooltip: 'Refresh', onPressed: _load)],
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading) return const LoadingView(label: 'Loading healthspan score…');
    if (_error != null) return ErrorView(message: _error!, onRetry: _load);
    final summary = _summary;
    if (summary == null) return const EmptyView(icon: Icons.favorite_border, title: 'No data');

    final score = _sixMonth ? summary.sixMonth : summary.thirtyDay;

    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: [
          _DisclaimerBanner(disclaimer: score.disclaimer),
          const SizedBox(height: AppSpacing.lg),
          Center(
            child: SegmentedButton<bool>(
              segments: const [
                ButtonSegment(value: false, label: Text('30 days')),
                ButtonSegment(value: true, label: Text('6 months')),
              ],
              selected: {_sixMonth},
              showSelectedIcon: false,
              onSelectionChanged: (s) => setState(() => _sixMonth = s.first),
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
          _ScoreCard(score: score),
          if (score.factors.isNotEmpty) ...[
            const SizedBox(height: AppSpacing.lg),
            _FactorsCard(score: score),
          ],
          const SizedBox(height: AppSpacing.lg),
          _MethodologyCard(score: score),
        ],
      ),
    );
  }
}

class _DisclaimerBanner extends StatelessWidget {
  final String disclaimer;
  const _DisclaimerBanner({required this.disclaimer});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(AppRadius.md),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.info_outline, size: 18, color: theme.colorScheme.onSurfaceVariant),
          const SizedBox(width: AppSpacing.sm),
          Expanded(child: Text(disclaimer, style: theme.textTheme.bodySmall)),
        ],
      ),
    );
  }
}

class _ScoreCard extends StatelessWidget {
  final HealthspanScoreResponse score;
  const _ScoreCard({required this.score});

  Color _confidenceColor(BuildContext context) {
    final status = Theme.of(context).status;
    switch (score.confidence) {
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
    final value = score.score;

    if (value == null) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.xl),
          child: Column(
            children: [
              Icon(Icons.favorite_border, size: 40, color: theme.colorScheme.onSurfaceVariant),
              const SizedBox(height: AppSpacing.md),
              Text(
                'Not enough real history yet',
                style: theme.textTheme.titleMedium,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: AppSpacing.sm),
              ...score.limitations.map((l) => Padding(
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
            Text('HEALTHSPAN SCORE', style: theme.textTheme.labelSmall),
            const SizedBox(height: AppSpacing.sm),
            Text('$value', style: theme.textTheme.displayLarge?.copyWith(fontSize: 48)),
            Text('out of 100', style: theme.textTheme.bodySmall),
            const SizedBox(height: AppSpacing.sm),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md, vertical: AppSpacing.xs),
              decoration: BoxDecoration(color: color.withValues(alpha: 0.15), borderRadius: BorderRadius.circular(AppRadius.pill)),
              child: Text(
                'CONFIDENCE: ${score.confidence.toUpperCase()}',
                style: theme.textTheme.labelMedium?.copyWith(color: color, fontWeight: FontWeight.w700),
              ),
            ),
            if (score.missingDataTreatment != 'All factors present.') ...[
              const SizedBox(height: AppSpacing.md),
              Text(score.missingDataTreatment, style: theme.textTheme.bodySmall, textAlign: TextAlign.center),
            ],
          ],
        ),
      ),
    );
  }
}

class _FactorsCard extends StatelessWidget {
  final HealthspanScoreResponse score;
  const _FactorsCard({required this.score});

  IconData _directionIcon(String direction) => switch (direction) {
        'FAVORABLE' => Icons.trending_up,
        'UNFAVORABLE' => Icons.trending_down,
        _ => Icons.trending_flat,
      };

  Color _directionColor(BuildContext context, String direction) {
    final status = Theme.of(context).status;
    return switch (direction) {
      'FAVORABLE' => status.good,
      'UNFAVORABLE' => status.poor,
      _ => Theme.of(context).colorScheme.onSurfaceVariant,
    };
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Contributing factors', style: theme.textTheme.titleMedium),
            const SizedBox(height: AppSpacing.xs),
            Text(
              'Every factor compares your own recent period against your own prior period — never a fixed population norm.',
              style: theme.textTheme.bodySmall,
            ),
            const SizedBox(height: AppSpacing.md),
            ...score.factors.map((f) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: AppSpacing.sm),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(_directionIcon(f.direction), size: 20, color: _directionColor(context, f.direction)),
                      const SizedBox(width: AppSpacing.sm),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Expanded(child: Text(f.name, style: theme.textTheme.bodyLarge)),
                                Text('${(f.weight * 100).round()}% weight', style: theme.textTheme.bodySmall),
                              ],
                            ),
                            const SizedBox(height: 2),
                            Text(f.comparison, style: theme.textTheme.bodySmall),
                          ],
                        ),
                      ),
                    ],
                  ),
                )),
          ],
        ),
      ),
    );
  }
}

class _MethodologyCard extends StatelessWidget {
  final HealthspanScoreResponse score;
  const _MethodologyCard({required this.score});

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
            Text(score.methodology, style: theme.textTheme.bodySmall),
            const SizedBox(height: AppSpacing.md),
            const Divider(height: 1),
            const SizedBox(height: AppSpacing.md),
            Text('Limitations', style: theme.textTheme.titleSmall),
            const SizedBox(height: AppSpacing.xs),
            ...score.limitations.map((l) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 2),
                  child: Text('• $l', style: theme.textTheme.bodySmall),
                )),
          ],
        ),
      ),
    );
  }
}
