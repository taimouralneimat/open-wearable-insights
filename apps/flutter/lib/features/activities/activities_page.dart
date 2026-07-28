import 'package:flutter/material.dart';
import '../../data/api_client.dart';

/// Activities page — shows daily activity summary and 7-day trends.
class ActivitiesPage extends StatefulWidget {
  const ActivitiesPage({super.key});

  @override
  State<ActivitiesPage> createState() => _ActivitiesPageState();
}

class _ActivitiesPageState extends State<ActivitiesPage> {
  final _apiClient = ApiClient();
  ActivitySummary? _summary;
  List<ActivityTrendPoint>? _trends;
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
      final summary = await _apiClient.getActivitySummary();
      final trends = await _apiClient.getActivityTrends();
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
        title: const Text('Activities'),
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
          Text('Loading activity data...'),
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
          const Text('Couldn\'t load activity data', style: TextStyle(fontSize: 20)),
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
          Icon(Icons.directions_run_outlined, size: 64, color: Colors.grey),
          SizedBox(height: 16),
          Text('No activity data yet', style: TextStyle(fontSize: 20)),
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
          _ActivitySummaryCard(summary: s),
          const SizedBox(height: 16),
          if (_trends != null) _ActivityTrendsCard(trends: _trends!),
        ],
      ),
    );
  }
}

class _ActivitySummaryCard extends StatelessWidget {
  final ActivitySummary summary;
  const _ActivitySummaryCard({required this.summary});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            const Text('Today', style: TextStyle(color: Colors.grey, fontSize: 14)),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                _StatTile(
                  icon: Icons.directions_walk,
                  label: 'Steps',
                  value: '${summary.steps}',
                  color: Colors.blue,
                ),
                _StatTile(
                  icon: Icons.local_fire_department_outlined,
                  label: 'Calories',
                  value: '${summary.calories}',
                  color: Colors.orange,
                ),
                _StatTile(
                  icon: Icons.timer_outlined,
                  label: 'Active min',
                  value: '${summary.activeMinutes}',
                  color: Colors.green,
                ),
              ],
            ),
            const SizedBox(height: 12),
            const Divider(),
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.favorite, size: 16, color: Colors.red),
                const SizedBox(width: 8),
                Text('${summary.activeZoneMinutes} active zone minutes',
                    style: const TextStyle(fontSize: 14, color: Colors.grey)),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _StatTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final Color color;
  const _StatTile({
    required this.icon,
    required this.label,
    required this.value,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Icon(icon, color: color, size: 32),
        const SizedBox(height: 4),
        Text(value, style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: color)),
        Text(label, style: const TextStyle(color: Colors.grey, fontSize: 12)),
      ],
    );
  }
}

class _ActivityTrendsCard extends StatelessWidget {
  final List<ActivityTrendPoint> trends;
  const _ActivityTrendsCard({required this.trends});

  @override
  Widget build(BuildContext context) {
    final maxSteps = trends.map((t) => t.steps).reduce((a, b) => a > b ? a : b);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('7-Day Steps Trend', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
            const SizedBox(height: 12),
            ...trends.map((t) => _TrendBar(point: t, maxSteps: maxSteps)),
          ],
        ),
      ),
    );
  }
}

class _TrendBar extends StatelessWidget {
  final ActivityTrendPoint point;
  final int maxSteps;
  const _TrendBar({required this.point, required this.maxSteps});

  @override
  Widget build(BuildContext context) {
    final fraction = maxSteps > 0 ? (point.steps / maxSteps).clamp(0.0, 1.0) : 0.0;
    final color = point.steps >= 10000
        ? Colors.green
        : point.steps >= 7000
            ? Colors.blue
            : Colors.orange;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          SizedBox(
            width: 50,
            child: Text(point.date.substring(5),
                style: const TextStyle(color: Colors.grey, fontSize: 12)),
          ),
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
            width: 50,
            child: Text('${point.steps}',
                style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w500),
                textAlign: TextAlign.right),
          ),
        ],
      ),
    );
  }
}
