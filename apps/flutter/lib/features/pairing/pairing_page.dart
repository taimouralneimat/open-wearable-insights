import 'package:flutter/material.dart';
import '../../app/theme.dart';
import '../../data/token_store.dart';

/// One-time local pairing screen — shown whenever there's no valid stored
/// API token (first run, or after a 401 clears one). Replaces the
/// hardcoded-token stopgap; see ADR-0008 for why the backend requires this
/// at all.
class PairingPage extends StatefulWidget {
  const PairingPage({super.key});

  @override
  State<PairingPage> createState() => _PairingPageState();
}

class _PairingPageState extends State<PairingPage> {
  final _controller = TextEditingController();
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _connect() async {
    final token = _controller.text.trim();
    if (token.isEmpty) {
      setState(() => _error = 'Paste the token first.');
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    await TokenStore.write(token);
    // go_router's redirect (wired to TokenStore.paired via refreshListenable
    // in app.dart) routes into the app automatically once this flips true —
    // nothing else to do here. If the token turns out to be wrong, the
    // first real API call 401s, TokenStore.clear() flips it back, and the
    // router sends us straight back to this screen.
    if (mounted) setState(() => _saving = false);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: Padding(
                padding: const EdgeInsets.all(AppSpacing.xl),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Icon(Icons.lock_outline, size: 48, color: theme.colorScheme.primary),
                    const SizedBox(height: AppSpacing.lg),
                    Text(
                      'Connect to your local backend',
                      style: theme.textTheme.headlineSmall,
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: AppSpacing.sm),
                    Text(
                      'This app only ever talks to the backend running on this machine — '
                      'nothing leaves it. Find the local access token by running this in a terminal:',
                      style: theme.textTheme.bodyMedium,
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: AppSpacing.md),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(AppSpacing.md),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surfaceContainerHighest,
                        borderRadius: BorderRadius.circular(AppRadius.md),
                      ),
                      child: SelectableText(
                        'cat ~/.open-wearable-insights/local-api-token',
                        style: theme.textTheme.bodyMedium?.copyWith(fontFamily: 'monospace'),
                      ),
                    ),
                    const SizedBox(height: AppSpacing.xl),
                    TextField(
                      controller: _controller,
                      obscureText: true,
                      decoration: const InputDecoration(hintText: 'Paste the token here'),
                      onSubmitted: (_) => _connect(),
                    ),
                    if (_error != null) ...[
                      const SizedBox(height: AppSpacing.sm),
                      Text(_error!, style: theme.textTheme.bodySmall?.copyWith(color: theme.status.poor)),
                    ],
                    const SizedBox(height: AppSpacing.lg),
                    FilledButton(
                      onPressed: _saving ? null : _connect,
                      child: _saving
                          ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                          : const Text('Connect'),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
