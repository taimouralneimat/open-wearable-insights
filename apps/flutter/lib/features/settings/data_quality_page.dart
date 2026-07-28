import 'package:flutter/material.dart';
import '../../data/api_client.dart';
import '../../widgets/confidence_banner.dart';

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
        title: const Text('Data Quality'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: _loadData,
          ),
        ],
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading) return _buildLoading();
    if (_error != null) return _buildError();
    if (_summary == null) return _buildEmpty();
    return _buildPopulated();
  }

  Widget _buildLoading() {
    return const Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          CircularProgressIndicator(),
          SizedBox(height: 16),
          Text('Loading data quality...'),
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
          const Text('Couldn\'t load data quality', style: TextStyle(fontSize: 20)),
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
          Icon(Icons.analytics_outlined, size: 64, color: Colors.grey),
          SizedBox(height: 16),
          Text('No data quality metrics', style: TextStyle(fontSize: 20)),
        ],
      ),
    );
  }

  Widget _buildPopulated() {
    final s = _summary!;
    return RefreshIndicator(
      onRefresh: _loadData,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          ConfidenceBanner(confidence: s.confidence, limitations: s.limitations),
          _OverallQualityCard(summary: s),
          const SizedBox(height: 16),
          _MetricCoverageCard(summary: s),
          const SizedBox(height: 16),
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
    final completenessPct = (summary.completeness * 100).round();
    final color = completenessPct >= 80
        ? Colors.green
        : completenessPct >= 60
            ? Colors.orange
            : Colors.red;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            const Text('Overall Completeness', style: TextStyle(color: Colors.grey, fontSize: 14)),
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                Text('$completenessPct',
                    style: TextStyle(fontSize: 64, fontWeight: FontWeight.bold, color: color)),
                const Text('%', style: TextStyle(fontSize: 24, color: Colors.grey)),
              ],
            ),
            const SizedBox(height: 8),
            LinearProgressIndicator(
              value: summary.completeness,
              backgroundColor: Colors.grey.shade200,
              color: color,
              minHeight: 8,
            ),
            const SizedBox(height: 12),
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
    return Column(
      children: [
        Text(value, style: const TextStyle(fontWeight: FontWeight.w600)),
        Text(label, style: const TextStyle(color: Colors.grey, fontSize: 12)),
      ],
    );
  }
}

class _MetricCoverageCard extends StatelessWidget {
  final DataQualitySummary summary;
  const _MetricCoverageCard({required this.summary});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Metric Coverage', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
            const SizedBox(height: 12),
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
    final coveragePct = (metric.coverage * 100).round();
    final color = metric.quality == 'good'
        ? Colors.green
        : metric.quality == 'fair'
            ? Colors.orange
            : Colors.red;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          SizedBox(width: 80, child: Text(metric.metric)),
          const SizedBox(width: 8),
          Expanded(
            child: LinearProgressIndicator(
              value: metric.coverage,
              backgroundColor: Colors.grey.shade200,
              color: color,
              minHeight: 12,
            ),
          ),
          const SizedBox(width: 8),
          SizedBox(
            width: 40,
            child: Text('$coveragePct%',
                style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w500),
                textAlign: TextAlign.right),
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
    return Card(
      color: Colors.orange.shade50,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Row(
              children: [
                Icon(Icons.warning_amber, color: Colors.orange),
                SizedBox(width: 8),
                Text('Data Quality Issues',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: Colors.orange)),
              ],
            ),
            const SizedBox(height: 12),
            ...issues.map((issue) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('• ', style: TextStyle(color: Colors.orange)),
                      Expanded(child: Text(issue, style: const TextStyle(fontSize: 13))),
                    ],
                  ),
                )),
          ],
        ),
      ),
    );
  }
}
