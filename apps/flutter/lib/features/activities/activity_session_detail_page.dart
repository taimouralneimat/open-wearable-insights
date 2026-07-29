import 'package:flutter/material.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../widgets/state_views.dart';

/// Detail view for one discrete workout session (see ADR-0006 /
/// ActivitySessionResponse) — sport, duration, distance, pace, HR zones.
class ActivitySessionDetailPage extends StatefulWidget {
  final int id;
  const ActivitySessionDetailPage({super.key, required this.id});

  @override
  State<ActivitySessionDetailPage> createState() => _ActivitySessionDetailPageState();
}

class _ActivitySessionDetailPageState extends State<ActivitySessionDetailPage> {
  final _api = ApiClient();
  ActivitySessionResponse? _session;
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
      final session = await _api.getActivitySession(widget.id);
      if (!mounted) return;
      setState(() {
        _session = session;
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
      appBar: AppBar(title: Text(_session != null ? _sportLabel(_session!.sport) : 'Activity')),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading) return const LoadingView(label: 'Loading session…');
    if (_error != null) return ErrorView(message: _error!, onRetry: _load);
    final s = _session;
    if (s == null) return const EmptyView(icon: Icons.directions_run_outlined, title: 'Session not found');

    final theme = Theme.of(context);
    final start = DateTime.tryParse(s.startTime);

    return ListView(
      padding: const EdgeInsets.all(AppSpacing.lg),
      children: [
        if (start != null)
          Padding(
            padding: const EdgeInsets.only(bottom: AppSpacing.lg),
            child: Text(_formatDateTime(start), style: theme.textTheme.bodyMedium),
          ),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: Row(
              children: [
                Expanded(child: _Stat(label: 'Duration', value: _formatDuration(s.durationSeconds))),
                Expanded(child: _Stat(label: 'Distance', value: _formatDistance(s.distanceMeters))),
                Expanded(child: _Stat(label: 'Avg pace', value: _formatPace(s.avgPaceSecPerKm))),
              ],
            ),
          ),
        ),
        const SizedBox(height: AppSpacing.lg),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: Row(
              children: [
                Expanded(child: _Stat(label: 'Avg HR', value: s.avgHeartRate != null ? '${s.avgHeartRate} bpm' : '—')),
                Expanded(child: _Stat(label: 'Max HR', value: s.maxHeartRate != null ? '${s.maxHeartRate} bpm' : '—')),
                Expanded(child: _Stat(label: 'Calories', value: s.calories != null ? '${s.calories}' : '—')),
              ],
            ),
          ),
        ),
        if (s.hrZoneSeconds.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.lg),
          _HrZoneCard(hrZoneSeconds: s.hrZoneSeconds),
        ],
      ],
    );
  }

  String _sportLabel(String sport) => sport.isEmpty ? 'Activity' : sport[0].toUpperCase() + sport.substring(1);

  String _formatDateTime(DateTime dt) {
    final local = dt.toLocal();
    return '${local.year}-${local.month.toString().padLeft(2, '0')}-${local.day.toString().padLeft(2, '0')} '
        '${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
  }

  String _formatDuration(double seconds) {
    final total = seconds.round();
    final h = total ~/ 3600;
    final m = (total % 3600) ~/ 60;
    final s = total % 60;
    if (h > 0) return '${h}h ${m}m';
    return '${m}m ${s}s';
  }

  String _formatDistance(double? meters) {
    if (meters == null) return '—';
    return '${(meters / 1000).toStringAsFixed(2)} km';
  }

  String _formatPace(double? secPerKm) {
    if (secPerKm == null) return '—';
    final total = secPerKm.round();
    final m = total ~/ 60;
    final s = total % 60;
    return '$m:${s.toString().padLeft(2, '0')} /km';
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
        Text(value, style: theme.textTheme.headlineSmall, textAlign: TextAlign.center),
        const SizedBox(height: AppSpacing.xs),
        Text(label, style: theme.textTheme.bodySmall, textAlign: TextAlign.center),
      ],
    );
  }
}

/// HR-zone breakdown bar, zone index -> device-defined zone number (no
/// zone-boundary metadata is available from the source, so zones are
/// labeled generically rather than with fabricated bpm ranges).
class _HrZoneCard extends StatelessWidget {
  final List<double> hrZoneSeconds;
  const _HrZoneCard({required this.hrZoneSeconds});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final total = hrZoneSeconds.fold<double>(0, (a, b) => a + b);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Heart-rate zones', style: theme.textTheme.titleMedium),
            const SizedBox(height: AppSpacing.md),
            ...List.generate(hrZoneSeconds.length, (i) {
              final seconds = hrZoneSeconds[i];
              final fraction = total > 0 ? seconds / total : 0.0;
              return Padding(
                padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
                child: Row(
                  children: [
                    SizedBox(width: 56, child: Text('Zone $i', style: theme.textTheme.bodySmall)),
                    const SizedBox(width: AppSpacing.sm),
                    Expanded(
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(AppRadius.pill),
                        child: LinearProgressIndicator(value: fraction, minHeight: 10),
                      ),
                    ),
                    const SizedBox(width: AppSpacing.sm),
                    SizedBox(
                      width: 52,
                      child: Text('${(seconds / 60).round()}m', style: theme.textTheme.labelLarge, textAlign: TextAlign.right),
                    ),
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
