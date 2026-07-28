import 'package:flutter/material.dart';
import '../../data/api_client.dart';

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
      setState(() {
        _summary = summary;
        _trends = trends;
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
          Text('Loading sleep data...'),
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
          const Text('Couldn\'t load sleep data', style: TextStyle(fontSize: 20)),
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
          Icon(Icons.bedtime_outlined, size: 64, color: Colors.grey),
          SizedBox(height: 16),
          Text('No sleep data yet', style: TextStyle(fontSize: 20)),
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
          _SleepScoreCard(summary: s),
          const SizedBox(height: 16),
          _SleepStagesCard(summary: s),
          const SizedBox(height: 16),
          if (_trends != null) _SleepTrendsCard(trends: _trends!),
        ],
      ),
    );
  }
}

class _SleepScoreCard extends StatelessWidget {
  final SleepSummary summary;
  const _SleepScoreCard({required this.summary});

  @override
  Widget build(BuildContext context) {
    final color = summary.sleepScore >= 80
        ? Colors.green
        : summary.sleepScore >= 60
            ? Colors.orange
            : Colors.red;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            const Text('Last Night', style: TextStyle(color: Colors.grey, fontSize: 14)),
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                Text('${summary.sleepScore}',
                    style: TextStyle(fontSize: 64, fontWeight: FontWeight.bold, color: color)),
                const Text('/100', style: TextStyle(fontSize: 24, color: Colors.grey)),
              ],
            ),
            const SizedBox(height: 8),
            Text('${summary.totalHours.toStringAsFixed(1)}h total sleep',
                style: const TextStyle(fontSize: 16)),
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
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Sleep Stages', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
            const SizedBox(height: 12),
            _StageBar(label: 'Deep', hours: summary.deepHours, color: Colors.indigo),
            _StageBar(label: 'REM', hours: summary.remHours, color: Colors.purple),
            _StageBar(label: 'Light', hours: summary.lightHours, color: Colors.blue),
            _StageBar(label: 'Awake', hours: summary.awakeHours, color: Colors.orange),
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
    const maxHours = 4.0;
    final fraction = (hours / maxHours).clamp(0.0, 1.0);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          SizedBox(width: 60, child: Text(label)),
          const SizedBox(width: 8),
          Expanded(
            child: LinearProgressIndicator(
              value: fraction,
              backgroundColor: Colors.grey.shade200,
              color: color,
              minHeight: 12,
            ),
          ),
          const SizedBox(width: 8),
          SizedBox(
            width: 40,
            child: Text('${hours.toStringAsFixed(1)}h',
                style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w500),
                textAlign: TextAlign.right),
          ),
        ],
      ),
    );
  }
}

class _SleepTrendsCard extends StatelessWidget {
  final List<SleepTrendPoint> trends;
  const _SleepTrendsCard({required this.trends});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('7-Day Trend', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
            const SizedBox(height: 12),
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
    final color = point.sleepScore >= 80
        ? Colors.green
        : point.sleepScore >= 60
            ? Colors.orange
            : Colors.red;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          SizedBox(
            width: 80,
            child: Text(point.date.substring(5),
                style: const TextStyle(color: Colors.grey, fontSize: 12)),
          ),
          Expanded(
            child: Text('${point.totalHours.toStringAsFixed(1)}h',
                style: const TextStyle(fontWeight: FontWeight.w500)),
          ),
          SizedBox(
            width: 40,
            child: Text('${point.sleepScore}',
                style: TextStyle(color: color, fontWeight: FontWeight.w600),
                textAlign: TextAlign.right),
          ),
        ],
      ),
    );
  }
}
