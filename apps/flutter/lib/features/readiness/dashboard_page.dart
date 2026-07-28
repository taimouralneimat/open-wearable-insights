import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../data/api_client.dart';

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
      setState(() {
        _readiness = readiness;
        _insight = insight;
        _scoreDiff = diff;
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
        title: const Text('Open Wearable Insights'),
        actions: [
          IconButton(
            icon: const Icon(Icons.bedtime_outlined),
            tooltip: 'Sleep',
            onPressed: () => context.push('/sleep'),
          ),
          IconButton(
            icon: const Icon(Icons.directions_run_outlined),
            tooltip: 'Activities',
            onPressed: () => context.push('/activities'),
          ),
          IconButton(
            icon: const Icon(Icons.analytics_outlined),
            tooltip: 'Data Quality',
            onPressed: () => context.push('/data-quality'),
          ),
          IconButton(
            icon: const Icon(Icons.upload_file_outlined),
            tooltip: 'Import data',
            onPressed: () => context.push('/import'),
          ),
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: _loadData,
          ),
          IconButton(
            icon: const Icon(Icons.psychology_outlined),
            tooltip: 'LLM: fallback (deterministic)',
            onPressed: () {},
          ),
        ],
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading) return _buildLoading();
    if (_error != null) return _buildError();
    if (_readiness == null) return _buildEmpty();
    return _buildPopulated();
  }

  Widget _buildLoading() {
    return const Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          CircularProgressIndicator(),
          SizedBox(height: 16),
          Text('Loading readiness...'),
        ],
      ),
    );
  }

  Widget _buildError() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.error_outline, size: 64, color: Colors.red),
          const SizedBox(height: 16),
          const Text('Couldn\'t load dashboard', style: TextStyle(fontSize: 20)),
          const SizedBox(height: 8),
          Text(_error!, style: const TextStyle(color: Colors.grey), textAlign: TextAlign.center),
          const SizedBox(height: 16),
          ElevatedButton(onPressed: _loadData, child: const Text('Retry')),
        ],
      ),
    );
  }

  Widget _buildEmpty() {
    return const Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.monitor_heart_outlined, size: 64, color: Colors.grey),
          SizedBox(height: 16),
          Text('No data yet', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w500)),
          SizedBox(height: 8),
          Text(
            'Run ./scripts/load-synthetic.sh or import data\nto see your readiness score.',
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.grey),
          ),
        ],
      ),
    );
  }

  Widget _buildPopulated() {
    final r = _readiness!;
    final i = _insight;
    return RefreshIndicator(
      onRefresh: _loadData,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _ReadinessCard(readiness: r),
          if (_scoreDiff != null) ...[
            const SizedBox(height: 16),
            _ScoreDiffCard(diff: _scoreDiff!),
          ],
          const SizedBox(height: 16),
          if (i != null) ...[
            _CoachCard(insight: i),
            const SizedBox(height: 16),
          ],
          _FactorsCard(readiness: r),
          const SizedBox(height: 16),
          _DataQualityCard(readiness: r),
        ],
      ),
    );
  }
}


class _ScoreDiffCard extends StatelessWidget {
  final ScoreDiffResponse diff;
  const _ScoreDiffCard({required this.diff});

  @override
  Widget build(BuildContext context) {
    final deltaColor = diff.scoreDelta > 0
        ? Colors.green
        : diff.scoreDelta < 0
            ? Colors.red
            : Colors.grey;
    final deltaIcon = diff.scoreDelta > 0
        ? Icons.trending_up
        : diff.scoreDelta < 0
            ? Icons.trending_down
            : Icons.trending_flat;
    final deltaText = diff.scoreDelta > 0
        ? '+${diff.scoreDelta}'
        : '${diff.scoreDelta}';

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.compare_arrows, size: 20, color: Colors.blue),
                const SizedBox(width: 8),
                Text(diff.hasPriorData ? 'vs Yesterday' : 'Day-over-day',
                    style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
                const Spacer(),
                if (diff.hasPriorData) ...[
                  Icon(deltaIcon, color: deltaColor, size: 20),
                  const SizedBox(width: 4),
                  Text(deltaText,
                      style: TextStyle(
                          color: deltaColor,
                          fontSize: 18,
                          fontWeight: FontWeight.bold)),
                ],
              ],
            ),
            const SizedBox(height: 8),
            Text(diff.summary, style: const TextStyle(fontSize: 13)),
            const SizedBox(height: 12),
            ...diff.factorDiffs.where((f) => f.direction != 'unchanged').map((f) => _FactorDiffRow(diff: f)),
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
    final color = diff.direction == 'improved'
        ? Colors.green
        : Colors.red;
    final icon = diff.direction == 'improved'
        ? Icons.arrow_upward
        : Icons.arrow_downward;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        children: [
          Icon(icon, color: color, size: 14),
          const SizedBox(width: 8),
          Expanded(child: Text(diff.name, style: const TextStyle(fontSize: 12))),
          Text(
            diff.contributionDelta > 0 ? '+${diff.contributionDelta.toStringAsFixed(1)}' : diff.contributionDelta.toStringAsFixed(1),
            style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w500),
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
    final color = readiness.score >= 75
        ? Colors.green
        : readiness.score >= 50
            ? Colors.orange
            : Colors.red;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                Text('${readiness.score}',
                    style: TextStyle(fontSize: 64, fontWeight: FontWeight.bold, color: color)),
                const Text('/100', style: TextStyle(fontSize: 24, color: Colors.grey)),
              ],
            ),
            const SizedBox(height: 8),
            if (readiness.provisional)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                decoration: BoxDecoration(
                  color: Colors.orange.shade100,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Text('provisional', style: TextStyle(color: Colors.orange)),
              ),
            const SizedBox(height: 8),
            Text('Algorithm v${readiness.algorithmVersion} - ${readiness.baselinePeriod}',
                style: const TextStyle(color: Colors.grey, fontSize: 12)),
            const SizedBox(height: 12),
            Text(readiness.explanation, textAlign: TextAlign.center),
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
    final insight = widget.insight;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.lightbulb_outline, size: 20),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(insight.headline,
                      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
                ),
                TextButton.icon(
                  onPressed: _expanded ? null : _askWhy,
                  icon: const Icon(Icons.help_outline, size: 16),
                  label: const Text('Why?'),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(insight.summary),
            const SizedBox(height: 12),
            const Text('Recommended actions:', style: TextStyle(fontWeight: FontWeight.w500)),
            ...insight.recommendedActions.map((a) => Text('- $a')),
            const SizedBox(height: 8),
            if (insight.cautions.isNotEmpty) ...[
              const Text('Cautions:', style: TextStyle(fontWeight: FontWeight.w500, color: Colors.orange)),
              ...insight.cautions.map((c) => Text('- $c', style: const TextStyle(color: Colors.grey))),
            ],
            if (_expanded) ...[
              const Divider(height: 24),
              if (_whyLoading) const Center(child: CircularProgressIndicator()),
              if (_whyError != null)
                Text('Could not load explanation: $_whyError',
                    style: const TextStyle(color: Colors.red, fontSize: 12)),
              if (_why != null) _WhyAnswerSection(why: _why!),
            ],
          ],
        ),
      ),
    );
  }
}

class _WhyAnswerSection extends StatelessWidget {
  final WhyAnswerResponse why;
  const _WhyAnswerSection({required this.why});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(why.answer, style: const TextStyle(fontStyle: FontStyle.italic)),
        const SizedBox(height: 12),
        const Text('Cited metrics:', style: TextStyle(fontWeight: FontWeight.w500)),
        const SizedBox(height: 4),
        ...why.citedMetrics.map((m) {
          final color = m.direction == 'positive'
              ? Colors.green
              : m.direction == 'negative'
                  ? Colors.red
                  : Colors.grey;
          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 2),
            child: Row(
              children: [
                Expanded(child: Text('${m.name}: ${m.value}')),
                Text(m.contribution, style: TextStyle(color: color, fontWeight: FontWeight.w500)),
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
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Factor Contributions', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
            const SizedBox(height: 12),
            ...readiness.factors.map((f) => _FactorRow(factor: f)),
            const SizedBox(height: 8),
            Text(readiness.missingDataTreatment,
                style: const TextStyle(color: Colors.grey, fontSize: 12)),
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
    final icon = factor.direction == 'positive'
        ? Icons.arrow_upward
        : factor.direction == 'negative'
            ? Icons.arrow_downward
            : Icons.remove;
    final color = factor.direction == 'positive'
        ? Colors.green
        : factor.direction == 'negative'
            ? Colors.red
            : Colors.grey;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Icon(icon, color: color, size: 16),
          const SizedBox(width: 8),
          Expanded(child: Text(factor.name)),
          Text(factor.contribution.toStringAsFixed(1),
              style: TextStyle(color: color, fontWeight: FontWeight.w500)),
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
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Data Quality', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            _QualityRow(label: 'Confidence', value: readiness.confidence),
            _QualityRow(label: 'Data quality', value: readiness.dataQuality),
            _QualityRow(label: 'Baseline', value: readiness.baselinePeriod),
            const SizedBox(height: 8),
            Text('Limitations: ${readiness.limitations}',
                style: const TextStyle(color: Colors.grey, fontSize: 12)),
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
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        children: [
          Text(label, style: const TextStyle(color: Colors.grey)),
          const Spacer(),
          Text(value, style: const TextStyle(fontWeight: FontWeight.w500)),
        ],
      ),
    );
  }
}