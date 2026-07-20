import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Dashboard page — shows readiness score, factor contributions, and daily coach.
///
/// Handles all states: empty, loading, calibration, stale, partial, error, populated.
class DashboardPage extends ConsumerWidget {
  const DashboardPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Open Wearable Insights'),
        actions: [
          // LLM status indicator
          IconButton(
            icon: const Icon(Icons.psychology_outlined),
            tooltip: 'LLM: fallback (deterministic)',
            onPressed: () {},
          ),
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            tooltip: 'Settings',
            onPressed: () {},
          ),
        ],
      ),
      body: const _DashboardBody(),
    );
  }
}

class _DashboardBody extends StatelessWidget {
  const _DashboardBody();

  @override
  Widget build(BuildContext context) {
    // Phase 0: empty state placeholder.
    // Phase 1 will wire this to the readiness provider.
    return const Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.monitor_heart_outlined, size: 64, color: Colors.grey),
          SizedBox(height: 16),
          Text(
            'No data yet',
            style: TextStyle(fontSize: 20, fontWeight: FontWeight.w500),
          ),
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
}