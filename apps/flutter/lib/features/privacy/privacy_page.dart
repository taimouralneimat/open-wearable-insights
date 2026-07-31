import 'package:flutter/material.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../data/web_download.dart';
import '../../widgets/state_views.dart';

/// Privacy page — the local-first data model in plain language, LLM status,
/// and the data-export action (moved here from Profile, where it lived as a
/// stopgap because there was nowhere else to put it).
///
/// Language here is pulled from docs/security/privacy-model.md rather than
/// invented — this screen makes real, checkable claims, not marketing copy.
class PrivacyPage extends StatefulWidget {
  const PrivacyPage({super.key});

  @override
  State<PrivacyPage> createState() => _PrivacyPageState();
}

class _PrivacyPageState extends State<PrivacyPage> {
  final _apiClient = ApiClient();
  LlmStatus? _llmStatus;
  bool _loading = true;
  bool _exporting = false;
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
      final status = await _apiClient.getLlmStatus();
      if (!mounted) return;
      setState(() {
        _llmStatus = status;
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

  Future<void> _exportData() async {
    setState(() => _exporting = true);
    try {
      final (body, filename) = await _apiClient.fetchFullExport();
      downloadTextAsFile(body, filename);
      if (!mounted) return;
      setState(() => _exporting = false);
    } catch (e) {
      if (!mounted) return;
      setState(() => _exporting = false);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Export failed: $e')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Privacy')),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading) return const LoadingView(label: 'Loading privacy status…');
    if (_error != null) return ErrorView(message: _error!, onRetry: _load);

    final theme = Theme.of(context);
    final llmEnabled = _llmStatus?.enabled ?? false;

    return ListView(
      padding: const EdgeInsets.all(AppSpacing.lg),
      children: [
        const _PrivacyFactCard(
          icon: Icons.lan_outlined,
          title: 'Local-first by default',
          body: 'This app talks only to a backend running on this machine — nothing is sent '
              'to an external analytics service, error-reporting service, or third-party API '
              'unless you explicitly enable it.',
        ),
        const SizedBox(height: AppSpacing.md),
        const _PrivacyFactCard(
          icon: Icons.visibility_off_outlined,
          title: 'No telemetry',
          body: 'No analytics or crash-reporting SDKs are built into this app. Nothing about '
              'how you use it is collected or transmitted anywhere.',
        ),
        const SizedBox(height: AppSpacing.md),
        _PrivacyFactCard(
          icon: llmEnabled ? Icons.cloud_outlined : Icons.smart_toy_outlined,
          title: llmEnabled ? 'Local AI coach: enabled' : 'Local AI coach: off (deterministic fallback)',
          body: llmEnabled
              ? 'The coach can use a local Ollama model running on this machine to phrase '
                  'explanations — it only ever receives a constrained summary of your computed '
                  'scores, never raw database access, and every score is still computed '
                  'deterministically, never by the model.'
              : 'The AI coach is currently using its deterministic template engine, not an LLM — '
                  'every explanation is computed directly from your real scores with no model '
                  'involved at all. The app is fully usable this way.',
        ),
        const SizedBox(height: AppSpacing.xxl),
        Text('Your data', style: theme.textTheme.titleMedium),
        const SizedBox(height: AppSpacing.sm),
        Text(
          'Download everything stored locally for this account — measurements, activities, '
          'journal entries, readiness history, and more — as a single JSON file. This is a '
          'complete local export; nothing is uploaded anywhere as part of it.',
          style: theme.textTheme.bodySmall,
        ),
        const SizedBox(height: AppSpacing.md),
        OutlinedButton.icon(
          onPressed: _exporting ? null : _exportData,
          icon: _exporting
              ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
              : const Icon(Icons.download_outlined, size: 18),
          label: Text(_exporting ? 'Preparing export…' : 'Export all my data'),
        ),
      ],
    );
  }
}

class _PrivacyFactCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String body;
  const _PrivacyFactCard({required this.icon, required this.title, required this.body});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, color: theme.colorScheme.primary),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: theme.textTheme.titleMedium),
                  const SizedBox(height: AppSpacing.xs),
                  Text(body, style: theme.textTheme.bodySmall),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
