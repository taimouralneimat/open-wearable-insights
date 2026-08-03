import 'package:flutter/material.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../widgets/state_views.dart';

/// Real Garmin Connect login — the only path in this app to actual
/// historical wearable data.
///
/// Garmin's official Health API requires a registered legal entity and is
/// closed to individual developers; Garmin Express's local sync folder only
/// ever holds a transient upload buffer, cleared once it's pushed to the
/// cloud (see the "Garmin Express" card on the Import page for that
/// opportunistic, non-historical path). This screen logs into the user's
/// own Garmin Connect account the same way Garmin's own mobile app does —
/// with the user's own credentials, entered here and nowhere else. Only the
/// resulting session tokens are stored; the password itself is sent once,
/// directly to the backend's login call, and never persisted or logged
/// (see GarminConnectAuthClient on the backend).
class GarminConnectorPage extends StatefulWidget {
  const GarminConnectorPage({super.key});

  @override
  State<GarminConnectorPage> createState() => _GarminConnectorPageState();
}

class _GarminConnectorPageState extends State<GarminConnectorPage> {
  final _apiClient = ApiClient();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _mfaController = TextEditingController();

  GarminConnectStatusResponse? _status;
  bool _loading = true;
  bool _submitting = false;
  bool _syncing = false;
  String? _formError;
  String? _mfaMethod;
  GarminConnectSyncResultResponse? _lastSyncResult;

  DateTime _syncStart = DateTime.now().subtract(const Duration(days: 90));
  DateTime _syncEnd = DateTime.now();

  @override
  void initState() {
    super.initState();
    _loadStatus();
  }

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    _mfaController.dispose();
    super.dispose();
  }

  Future<void> _loadStatus() async {
    setState(() => _loading = true);
    try {
      final status = await _apiClient.getGarminConnectStatus();
      if (!mounted) return;
      setState(() {
        _status = status;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _status = GarminConnectStatusResponse(status: 'error', lastError: e.toString());
        _loading = false;
      });
    }
  }

  Future<void> _connect() async {
    if (_emailController.text.trim().isEmpty || _passwordController.text.isEmpty) {
      setState(() => _formError = 'Enter your Garmin Connect email and password.');
      return;
    }
    setState(() {
      _submitting = true;
      _formError = null;
    });
    try {
      final result = await _apiClient.garminConnectLogin(
        _emailController.text.trim(),
        _passwordController.text,
      );
      if (!mounted) return;
      _passwordController.clear();
      if (result.status == 'mfa_required') {
        setState(() {
          _mfaMethod = result.mfaMethod;
          _submitting = false;
        });
      } else if (result.status == 'connected') {
        setState(() => _submitting = false);
        await _loadStatus();
      } else {
        setState(() {
          _formError = result.message ?? 'Login failed.';
          _submitting = false;
        });
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _formError = 'Could not reach the backend: $e';
        _submitting = false;
      });
    }
  }

  Future<void> _submitMfa() async {
    if (_mfaController.text.trim().isEmpty) return;
    setState(() {
      _submitting = true;
      _formError = null;
    });
    try {
      final result = await _apiClient.garminConnectSubmitMfa(_mfaController.text.trim());
      if (!mounted) return;
      _mfaController.clear();
      if (result.status == 'connected') {
        setState(() {
          _mfaMethod = null;
          _submitting = false;
        });
        await _loadStatus();
      } else {
        setState(() {
          _formError = result.message ?? 'Verification failed.';
          _submitting = false;
        });
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _formError = 'Could not reach the backend: $e';
        _submitting = false;
      });
    }
  }

  Future<void> _disconnect() async {
    setState(() => _submitting = true);
    try {
      await _apiClient.garminConnectDisconnect();
      if (!mounted) return;
      setState(() {
        _submitting = false;
        _lastSyncResult = null;
      });
      await _loadStatus();
    } catch (e) {
      if (!mounted) return;
      setState(() => _submitting = false);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Disconnect failed: $e')));
    }
  }

  Future<void> _sync() async {
    setState(() {
      _syncing = true;
      _lastSyncResult = null;
    });
    try {
      final result = await _apiClient.garminConnectSync(_syncStart, _syncEnd);
      if (!mounted) return;
      setState(() {
        _lastSyncResult = result;
        _syncing = false;
      });
      await _loadStatus();
    } catch (e) {
      if (!mounted) return;
      setState(() => _syncing = false);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Sync failed: $e')));
    }
  }

  Future<void> _pickDate({required bool isStart}) async {
    final picked = await showDatePicker(
      context: context,
      initialDate: isStart ? _syncStart : _syncEnd,
      firstDate: DateTime(2015),
      lastDate: DateTime.now(),
    );
    if (picked == null) return;
    setState(() {
      if (isStart) {
        _syncStart = picked;
      } else {
        _syncEnd = picked;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Garmin Connect')),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading) return const LoadingView(label: 'Checking Garmin Connect status…');
    final status = _status;
    if (status == null) return ErrorView(message: 'Could not load status', onRetry: _loadStatus);

    if (status.status == 'connected') return _buildConnected(status);
    if (_mfaMethod != null || status.status == 'mfa_required') return _buildMfaForm();
    return _buildLoginForm(status);
  }

  Widget _buildLoginForm(GarminConnectStatusResponse status) {
    final theme = Theme.of(context);
    return ListView(
      padding: const EdgeInsets.all(AppSpacing.lg),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(Icons.lock_outline, color: theme.status.good, size: 18),
                    const SizedBox(width: AppSpacing.sm),
                    Expanded(
                      child: Text(
                        'Your password is sent once, directly to Garmin, to log in — it is never '
                        'stored. Only the resulting session is kept, so future syncs don\'t need it again.',
                        style: theme.textTheme.bodySmall,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: AppSpacing.lg),
        Text('Log in to Garmin Connect', style: theme.textTheme.titleMedium),
        const SizedBox(height: AppSpacing.md),
        TextField(
          controller: _emailController,
          decoration: const InputDecoration(labelText: 'Garmin Connect email', border: OutlineInputBorder()),
          keyboardType: TextInputType.emailAddress,
          enabled: !_submitting,
        ),
        const SizedBox(height: AppSpacing.md),
        TextField(
          controller: _passwordController,
          decoration: const InputDecoration(labelText: 'Password', border: OutlineInputBorder()),
          obscureText: true,
          enabled: !_submitting,
          onSubmitted: (_) => _connect(),
        ),
        if (_formError != null) ...[
          const SizedBox(height: AppSpacing.sm),
          Text(_formError!, style: TextStyle(color: theme.status.poor)),
        ] else if (status.status == 'error' && status.lastError != null) ...[
          const SizedBox(height: AppSpacing.sm),
          Text(status.lastError!, style: TextStyle(color: theme.status.poor)),
        ],
        const SizedBox(height: AppSpacing.lg),
        FilledButton(
          onPressed: _submitting ? null : _connect,
          child: _submitting
              ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Connect'),
        ),
      ],
    );
  }

  Widget _buildMfaForm() {
    final theme = Theme.of(context);
    final method = _mfaMethod ?? 'email';
    return ListView(
      padding: const EdgeInsets.all(AppSpacing.lg),
      children: [
        Text('Verification needed', style: theme.textTheme.titleMedium),
        const SizedBox(height: AppSpacing.sm),
        Text('Garmin sent a code via $method. Enter it below to finish connecting.',
            style: theme.textTheme.bodyMedium),
        const SizedBox(height: AppSpacing.lg),
        TextField(
          controller: _mfaController,
          decoration: const InputDecoration(labelText: 'Verification code', border: OutlineInputBorder()),
          keyboardType: TextInputType.number,
          enabled: !_submitting,
          onSubmitted: (_) => _submitMfa(),
        ),
        if (_formError != null) ...[
          const SizedBox(height: AppSpacing.sm),
          Text(_formError!, style: TextStyle(color: theme.status.poor)),
        ],
        const SizedBox(height: AppSpacing.lg),
        FilledButton(
          onPressed: _submitting ? null : _submitMfa,
          child: _submitting
              ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Verify'),
        ),
        const SizedBox(height: AppSpacing.sm),
        TextButton(
          onPressed: _submitting ? null : () => setState(() => _mfaMethod = null),
          child: const Text('Start over'),
        ),
      ],
    );
  }

  Widget _buildConnected(GarminConnectStatusResponse status) {
    final theme = Theme.of(context);
    return ListView(
      padding: const EdgeInsets.all(AppSpacing.lg),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: Row(
              children: [
                Icon(Icons.check_circle_outline, color: theme.status.good),
                const SizedBox(width: AppSpacing.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Connected as ${status.email}', style: theme.textTheme.titleMedium),
                      Text(
                        status.lastSyncAt != null
                            ? 'Last synced: ${status.lastSyncAt}'
                            : 'Not synced yet',
                        style: theme.textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: AppSpacing.xl),
        Text('Pull historical data', style: theme.textTheme.titleMedium),
        const SizedBox(height: AppSpacing.sm),
        Text(
          'Fetches sleep, HRV, heart rate, stress, Body Battery, training readiness, calories, '
          'and more for the date range below, directly from your Garmin Connect account.',
          style: theme.textTheme.bodySmall,
        ),
        const SizedBox(height: AppSpacing.xs),
        Text(
          'Large date ranges can take several minutes — this is normal, keep the app open.',
          style: theme.textTheme.bodySmall?.copyWith(fontStyle: FontStyle.italic),
        ),
        const SizedBox(height: AppSpacing.md),
        Row(
          children: [
            Expanded(
              child: OutlinedButton(
                onPressed: _syncing ? null : () => _pickDate(isStart: true),
                child: Text('From: ${_formatDate(_syncStart)}'),
              ),
            ),
            const SizedBox(width: AppSpacing.sm),
            Expanded(
              child: OutlinedButton(
                onPressed: _syncing ? null : () => _pickDate(isStart: false),
                child: Text('To: ${_formatDate(_syncEnd)}'),
              ),
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.md),
        FilledButton.icon(
          onPressed: _syncing ? null : _sync,
          icon: _syncing
              ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
              : const Icon(Icons.sync),
          label: Text(_syncing ? 'Syncing…' : 'Sync now'),
        ),
        if (_lastSyncResult != null) ...[
          const SizedBox(height: AppSpacing.lg),
          _SyncResultCard(result: _lastSyncResult!),
        ],
        const SizedBox(height: AppSpacing.xxl),
        OutlinedButton(
          onPressed: _submitting ? null : _disconnect,
          style: OutlinedButton.styleFrom(foregroundColor: theme.status.poor),
          child: const Text('Disconnect'),
        ),
      ],
    );
  }

  String _formatDate(DateTime d) => '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
}

class _SyncResultCard extends StatelessWidget {
  final GarminConnectSyncResultResponse result;
  const _SyncResultCard({required this.result});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Sync complete', style: theme.textTheme.titleSmall),
            const SizedBox(height: AppSpacing.xs),
            Text(
              '${result.daysWithData}/${result.daysAttempted} days had data · '
              '${result.measurementsWritten} measurements written',
              style: theme.textTheme.bodyMedium,
            ),
            if (result.errors.isNotEmpty) ...[
              const SizedBox(height: AppSpacing.sm),
              Text('${result.errors.length} day(s) failed:', style: theme.textTheme.bodySmall),
              ...result.errors.take(5).map((e) => Text('· $e', style: theme.textTheme.bodySmall)),
            ],
          ],
        ),
      ),
    );
  }
}
