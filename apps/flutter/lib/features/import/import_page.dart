import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../widgets/state_views.dart';

/// Import page — guided UI for importing wearable data from a local folder.
///
/// The import folder is outside the repo (configured via OWI_IMPORT_DIR).
/// Real health data never enters the repo; only checksums/metadata are stored.
///
/// Handles all states: empty, loading, error, populated (with supported/
/// unsupported files).
///
/// Phase 2: includes dry-run validation with duplicate detection and
/// unsupported-record reporting.
class ImportPage extends StatefulWidget {
  const ImportPage({super.key});

  @override
  State<ImportPage> createState() => _ImportPageState();
}

class _ImportPageState extends State<ImportPage> {
  final _apiClient = ApiClient();
  ImportScanResult? _scanResult;
  DryRunSummary? _dryRunResult;
  ImportProgress? _importProgress;
  List<ImportBatchResponse> _batches = [];
  List<GarminExpressDeviceResponse> _garminExpressDevices = [];
  String? _stagingDeviceId;
  GarminConnectStatusResponse? _garminConnectStatus;
  bool _loading = true;
  bool _validating = false;
  bool _importing = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _scanDirectory();
    _discoverGarminExpress();
    _loadGarminConnectStatus();
  }

  Future<void> _loadGarminConnectStatus() async {
    try {
      final status = await _apiClient.getGarminConnectStatus();
      if (!mounted) return;
      setState(() => _garminConnectStatus = status);
    } catch (_) {
      // Best-effort — shown as "not connected" if the status call itself fails.
    }
  }

  Future<void> _discoverGarminExpress() async {
    try {
      final devices = await _apiClient.getGarminExpressDevices();
      if (!mounted) return;
      setState(() => _garminExpressDevices = devices);
    } catch (_) {
      // Best-effort — Garmin Express may not be installed at all, which is
      // a completely normal state, not an error worth surfacing.
    }
  }

  Future<void> _stageGarminExpressDevice(String deviceId) async {
    setState(() => _stagingDeviceId = deviceId);
    try {
      final copied = await _apiClient.stageGarminExpressDevice(deviceId);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(
              copied > 0 ? 'Copied $copied file${copied == 1 ? '' : 's'} into the import folder.' : 'Already up to date — nothing new to copy.')),
        );
      }
      await _scanDirectory();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Copy failed: $e')));
      }
    } finally {
      if (mounted) setState(() => _stagingDeviceId = null);
    }
  }

  Future<void> _scanDirectory() async {
    setState(() {
      _loading = true;
      _error = null;
      _dryRunResult = null;
    });
    try {
      final result = await _apiClient.scanImportDirectory();
      setState(() {
        _scanResult = result;
        _loading = false;
      });
      _loadBatches();
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  Future<void> _runImport() async {
    setState(() {
      _importing = true;
    });
    try {
      final result = await _apiClient.importAll();
      final batches = await _apiClient.listBatches();
      setState(() {
        _importProgress = result;
        _batches = batches;
        _importing = false;
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Imported ${result.imported} files, ${result.totalRecords} records. ${result.skipped} skipped, ${result.failed} failed.')),
        );
      }
    } catch (e) {
      setState(() {
        _importing = false;
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Import failed: $e')),
        );
      }
    }
  }

  Future<void> _undoBatch(int batchId) async {
    try {
      final result = await _apiClient.undoBatch(batchId);
      final batches = await _apiClient.listBatches();
      setState(() {
        _batches = batches;
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(result.message)),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Undo failed: $e')),
        );
      }
    }
  }

  Future<void> _loadBatches() async {
    try {
      final batches = await _apiClient.listBatches();
      setState(() {
        _batches = batches;
      });
    } catch (e) {
      // Silently fail — batches are supplementary
    }
  }

  Future<void> _runDryRun() async {
    setState(() {
      _validating = true;
    });
    try {
      final result = await _apiClient.dryRunValidation();
      setState(() {
        _dryRunResult = result;
        _validating = false;
      });
    } catch (e) {
      setState(() {
        _validating = false;
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Validation failed: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Import data'),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), tooltip: 'Rescan', onPressed: _scanDirectory),
          const SizedBox(width: AppSpacing.xs),
        ],
      ),
      body: Column(
        children: [
          _GarminConnectBanner(
            status: _garminConnectStatus,
            onTap: () async {
              await context.push('/import/garmin-connect');
              _loadGarminConnectStatus();
            },
          ),
          _BiomarkersBanner(onTap: () => context.push('/import/biomarkers')),
          Expanded(child: _buildBody()),
        ],
      ),
    );
  }

  Widget _buildBody() {
    if (_loading) return const LoadingView(label: 'Scanning import directory…');
    if (_error != null) return ErrorView(title: "Couldn't scan import directory", message: _error!, onRetry: _scanDirectory);
    if (_scanResult == null) return const EmptyView(icon: Icons.folder_off_outlined, title: 'No scan result');
    if (!_scanResult!.exists) return _buildDirectoryMissing();
    if (_scanResult!.files.isEmpty) return _buildNoFiles();
    return _buildFileList();
  }

  Widget _buildDirectoryMissing() {
    final theme = Theme.of(context);
    final dir = _scanResult?.directory ?? 'unknown';
    final msg = _scanResult?.errorMessage ?? 'Directory does not exist.';
    return SingleChildScrollView(
      padding: const EdgeInsets.all(AppSpacing.xxl),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.folder_off_outlined, size: 56, color: theme.status.fair),
          const SizedBox(height: AppSpacing.lg),
          Text('Import directory not found', style: theme.textTheme.titleLarge, textAlign: TextAlign.center),
          const SizedBox(height: AppSpacing.sm),
          Text(msg, textAlign: TextAlign.center, style: theme.textTheme.bodyMedium),
          const SizedBox(height: AppSpacing.xl),
          _buildGuidanceCard(dir),
        ],
      ),
    );
  }

  Widget _buildGuidanceCard(String dir) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('How to import data', style: theme.textTheme.titleMedium),
            const SizedBox(height: AppSpacing.md),
            Text('1. Create the import directory outside the repo:', style: theme.textTheme.bodyMedium),
            const SizedBox(height: AppSpacing.xs),
            _CodeBlock('mkdir -p "$dir"'),
            const SizedBox(height: AppSpacing.md),
            Text('2. Place your Garmin export files there:', style: theme.textTheme.bodyMedium),
            const SizedBox(height: AppSpacing.xs),
            _CodeBlock('cp ~/Downloads/garmin-export/* "$dir"/'),
            const SizedBox(height: AppSpacing.md),
            Text('3. Click rescan to detect your files.', style: theme.textTheme.bodyMedium),
            const SizedBox(height: AppSpacing.sm),
            Row(
              children: [
                Icon(Icons.lock_outline, size: 16, color: theme.status.good),
                const SizedBox(width: AppSpacing.sm),
                Expanded(
                  child: Text(
                    'Real health data stays outside the repo. '
                    'Only checksums and metadata are stored in the database.',
                    style: theme.textTheme.bodySmall,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildNoFiles() {
    final dir = _scanResult?.directory ?? 'unknown';
    if (_garminExpressDevices.isNotEmpty) {
      return SingleChildScrollView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (final device in _garminExpressDevices) ...[
              _GarminExpressCard(
                device: device,
                staging: _stagingDeviceId == device.deviceId,
                onStage: () => _stageGarminExpressDevice(device.deviceId),
              ),
              const SizedBox(height: AppSpacing.lg),
            ],
          ],
        ),
      );
    }
    return EmptyView(
      icon: Icons.folder_open_outlined,
      title: 'No files found',
      message: 'Place your export files in:\n$dir',
      action: FilledButton(onPressed: _scanDirectory, child: const Text('Rescan')),
    );
  }

  Widget _buildFileList() {
    final theme = Theme.of(context);
    final result = _scanResult!;
    final supported = result.files.where((f) => f.supported).toList();
    final unsupported = result.files.where((f) => !f.supported).toList();

    return ListView(
      padding: const EdgeInsets.all(AppSpacing.lg),
      children: [
        _DirectoryInfoCard(directory: result.directory),
        if (_garminExpressDevices.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.lg),
          for (final device in _garminExpressDevices) ...[
            _GarminExpressCard(
              device: device,
              staging: _stagingDeviceId == device.deviceId,
              onStage: () => _stageGarminExpressDevice(device.deviceId),
            ),
            const SizedBox(height: AppSpacing.sm),
          ],
        ],
        const SizedBox(height: AppSpacing.lg),
        Row(
          children: [
            Expanded(
              child: OutlinedButton.icon(
                onPressed: _validating ? null : _runDryRun,
                icon: _validating
                    ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                    : const Icon(Icons.fact_check_outlined),
                label: Text(_validating ? 'Validating…' : 'Dry-run'),
              ),
            ),
            const SizedBox(width: AppSpacing.sm),
            Expanded(
              child: FilledButton.icon(
                onPressed: _importing ? null : _runImport,
                icon: _importing
                    ? SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2, color: theme.colorScheme.onPrimary),
                      )
                    : const Icon(Icons.download_outlined),
                label: Text(_importing ? 'Importing…' : 'Import'),
              ),
            ),
          ],
        ),
        if (_dryRunResult != null) ...[
          const SizedBox(height: AppSpacing.lg),
          _DryRunSummaryCard(summary: _dryRunResult!),
        ],
        if (_importProgress != null) ...[
          const SizedBox(height: AppSpacing.lg),
          _ImportProgressCard(progress: _importProgress!),
        ],
        if (_batches.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.lg),
          _BatchesCard(batches: _batches, onUndo: _undoBatch),
        ],
        const SizedBox(height: AppSpacing.lg),
        if (supported.isNotEmpty) ...[
          _SectionHeader(title: 'Supported files', count: supported.length, icon: Icons.check_circle, color: theme.status.good),
          const SizedBox(height: AppSpacing.sm),
          ...supported.map((f) => Padding(
                padding: const EdgeInsets.only(bottom: AppSpacing.sm),
                child: _FileTile(file: f),
              )),
          const SizedBox(height: AppSpacing.sm),
        ],
        if (unsupported.isNotEmpty) ...[
          _SectionHeader(title: 'Unsupported files', count: unsupported.length, icon: Icons.warning_amber, color: theme.status.fair),
          const SizedBox(height: AppSpacing.sm),
          ...unsupported.map((f) => Padding(
                padding: const EdgeInsets.only(bottom: AppSpacing.sm),
                child: _FileTile(file: f),
              )),
          const SizedBox(height: AppSpacing.sm),
        ],
        const _PrivacyNoteCard(),
      ],
    );
  }
}

/// Always-visible entry point to the real Garmin Connect connector — the
/// only path in this app to actual historical data (Garmin Express only
/// ever holds a transient sync buffer; Garmin's official Health API is
/// closed to individual developers). Shown above the file-scan results
/// regardless of import-directory state, since a fresh install has no
/// import directory but should still be able to connect an account.
class _GarminConnectBanner extends StatelessWidget {
  final GarminConnectStatusResponse? status;
  final VoidCallback onTap;
  const _GarminConnectBanner({required this.status, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final s = status;
    final IconData icon;
    final Color color;
    final String title;
    final String subtitle;

    if (s == null) {
      icon = Icons.sync_outlined;
      color = theme.colorScheme.outline;
      title = 'Garmin Connect';
      subtitle = 'Checking connection…';
    } else if (s.status == 'connected') {
      icon = Icons.check_circle_outline;
      color = theme.status.good;
      title = 'Garmin Connect — connected';
      subtitle = s.lastSyncAt != null
          ? '${s.email} · last synced ${s.lastSyncAt}'
          : '${s.email} · not synced yet — tap to pull your history';
    } else if (s.status == 'mfa_required') {
      icon = Icons.pin_outlined;
      color = theme.status.fair;
      title = 'Garmin Connect — verification needed';
      subtitle = 'Enter the code Garmin sent to finish connecting';
    } else if (s.status == 'error') {
      icon = Icons.error_outline;
      color = theme.status.poor;
      title = 'Garmin Connect — connection issue';
      subtitle = s.lastError ?? 'Tap to try again';
    } else {
      icon = Icons.link_outlined;
      color = theme.colorScheme.primary;
      title = 'Connect Garmin Connect';
      subtitle = 'Log in with your Garmin account to pull your full history';
    }

    return Material(
      color: theme.colorScheme.surfaceContainerHighest,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg, vertical: AppSpacing.md),
          child: Row(
            children: [
              Icon(icon, color: color),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: theme.textTheme.titleSmall),
                    Text(subtitle, style: theme.textTheme.bodySmall, maxLines: 1, overflow: TextOverflow.ellipsis),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right),
            ],
          ),
        ),
      ),
    );
  }
}

/// Entry point to the blood biomarker (lab bloodwork) import screen —
/// docs/product/parity-matrix.md row 22. Biomarker CSVs use the exact same
/// import folder/dry-run/import flow as everything else on this page, so
/// this is a discovery banner, not a separate upload mechanism like Garmin
/// Connect's (which genuinely needs its own login flow). Always visible,
/// same placement rationale as [_GarminConnectBanner] above.
class _BiomarkersBanner extends StatelessWidget {
  final VoidCallback onTap;
  const _BiomarkersBanner({required this.onTap});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Material(
      color: theme.colorScheme.surfaceContainerHighest,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg, vertical: AppSpacing.md),
          child: Row(
            children: [
              Icon(Icons.science_outlined, color: theme.colorScheme.primary),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Blood biomarkers', style: theme.textTheme.titleSmall),
                    Text('Import lab bloodwork from a CSV — not medical advice',
                        style: theme.textTheme.bodySmall, maxLines: 1, overflow: TextOverflow.ellipsis),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right),
            ],
          ),
        ),
      ),
    );
  }
}

/// Real wellness data already staged locally by Garmin Express, before it
/// uploads to Garmin Connect's cloud — the only real path to sleep/steps/
/// stress/HRV data, since Garmin Connect's web export doesn't offer daily
/// wellness data at all, only per-activity workout files. Every action here
/// is an explicit button the user presses — nothing is copied automatically.
class _GarminExpressCard extends StatelessWidget {
  final GarminExpressDeviceResponse device;
  final bool staging;
  final VoidCallback onStage;
  const _GarminExpressCard({required this.device, required this.staging, required this.onStage});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    return Card(
      color: status.infoContainer,
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.watch_outlined, color: status.onInfoContainer),
                const SizedBox(width: AppSpacing.sm),
                Expanded(
                  child: Text(
                    'Found on Garmin Express',
                    style: theme.textTheme.titleMedium?.copyWith(color: status.onInfoContainer),
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.xs),
            Text(
              '${device.totalFiles} file${device.totalFiles == 1 ? '' : 's'} staged locally by Garmin '
              'Express, not yet copied here.',
              style: theme.textTheme.bodySmall?.copyWith(color: status.onInfoContainer),
            ),
            const SizedBox(height: AppSpacing.sm),
            Wrap(
              spacing: AppSpacing.sm,
              runSpacing: AppSpacing.xs,
              children: device.categories.map((c) {
                return Chip(
                  label: Text('${c.name}: ${c.fileCount}'),
                  backgroundColor: status.info.withValues(alpha: 0.15),
                  labelStyle: theme.textTheme.labelMedium?.copyWith(color: status.onInfoContainer),
                  side: BorderSide.none,
                );
              }).toList(),
            ),
            const SizedBox(height: AppSpacing.md),
            SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                onPressed: staging ? null : onStage,
                icon: staging
                    ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                    : const Icon(Icons.copy_outlined, size: 18),
                label: Text(staging ? 'Copying…' : 'Copy to import folder'),
              ),
            ),
            const SizedBox(height: AppSpacing.xs),
            Text(
              'Only copies (never moves) — your original files stay in Garmin Express untouched. '
              'You still choose when to dry-run and import below.',
              style: theme.textTheme.bodySmall?.copyWith(color: status.onInfoContainer.withValues(alpha: 0.8)),
            ),
          ],
        ),
      ),
    );
  }
}

class _CodeBlock extends StatelessWidget {
  final String code;
  const _CodeBlock(this.code);

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.sm),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(AppRadius.sm),
      ),
      child: SelectableText(code, style: theme.textTheme.bodySmall?.copyWith(fontFamily: 'monospace')),
    );
  }
}

class _ImportProgressCard extends StatelessWidget {
  final ImportProgress progress;
  const _ImportProgressCard({required this.progress});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    return Card(
      color: status.infoContainer,
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.download_done, color: status.onInfoContainer),
                const SizedBox(width: AppSpacing.sm),
                Text('Import results', style: theme.textTheme.titleMedium?.copyWith(color: status.onInfoContainer)),
              ],
            ),
            const SizedBox(height: AppSpacing.md),
            _ProgressStat(label: 'Imported', value: progress.imported, color: status.good),
            _ProgressStat(label: 'Skipped (duplicates/unsupported)', value: progress.skipped, color: status.fair),
            _ProgressStat(label: 'Failed', value: progress.failed, color: status.poor),
            _ProgressStat(label: 'Total records', value: progress.totalRecords, color: status.onInfoContainer),
            const SizedBox(height: AppSpacing.md),
            ...progress.files.map((f) => _FileImportTile(result: f)),
          ],
        ),
      ),
    );
  }
}

class _ProgressStat extends StatelessWidget {
  final String label;
  final int value;
  final Color color;
  const _ProgressStat({required this.label, required this.value, required this.color});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        children: [
          Text(label, style: theme.textTheme.bodyMedium),
          const Spacer(),
          Text('$value', style: theme.textTheme.labelLarge?.copyWith(color: color)),
        ],
      ),
    );
  }
}

class _FileImportTile extends StatelessWidget {
  final FileImportResult result;
  const _FileImportTile({required this.result});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    final color = result.status == 'imported'
        ? status.good
        : result.status == 'duplicate' || result.status == 'skipped'
            ? status.fair
            : status.poor;
    final icon = result.status == 'imported'
        ? Icons.check_circle
        : result.status == 'duplicate'
            ? Icons.content_copy
            : result.status == 'skipped'
                ? Icons.skip_next
                : Icons.error;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
      child: Row(
        children: [
          Icon(icon, color: color, size: 20),
          const SizedBox(width: AppSpacing.sm),
          Expanded(child: Text(result.filename, style: theme.textTheme.bodyMedium)),
          Text(result.status, style: theme.textTheme.labelMedium?.copyWith(color: color)),
        ],
      ),
    );
  }
}

class _BatchesCard extends StatelessWidget {
  final List<ImportBatchResponse> batches;
  final void Function(int) onUndo;
  const _BatchesCard({required this.batches, required this.onUndo});

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
                Icon(Icons.history, color: theme.colorScheme.primary),
                const SizedBox(width: AppSpacing.sm),
                Text('Import history', style: theme.textTheme.titleMedium),
              ],
            ),
            const SizedBox(height: AppSpacing.md),
            ...batches.map((b) => _BatchTile(batch: b, onUndo: onUndo)),
          ],
        ),
      ),
    );
  }
}

class _BatchTile extends StatelessWidget {
  final ImportBatchResponse batch;
  final void Function(int) onUndo;
  const _BatchTile({required this.batch, required this.onUndo});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    final isUndone = batch.status == 'undone';
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
      child: Row(
        children: [
          Icon(
            isUndone ? Icons.undo : Icons.check_circle,
            color: isUndone ? theme.colorScheme.onSurfaceVariant : status.good,
            size: 20,
          ),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(batch.fileName ?? 'Unknown file', style: theme.textTheme.bodyMedium),
                Text(
                  '${batch.source.toUpperCase()} · ${batch.recordCount} records · ${batch.importedAt.substring(0, 19)}',
                  style: theme.textTheme.bodySmall,
                ),
              ],
            ),
          ),
          if (!isUndone)
            TextButton(
              onPressed: () => onUndo(batch.id),
              child: Text('Undo', style: TextStyle(color: status.fair)),
            ),
          if (isUndone) Text('Undone', style: theme.textTheme.bodySmall),
        ],
      ),
    );
  }
}

class _DirectoryInfoCard extends StatelessWidget {
  final String directory;
  const _DirectoryInfoCard({required this.directory});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Row(
          children: [
            Icon(Icons.folder, color: theme.colorScheme.primary),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Import directory', style: theme.textTheme.labelLarge),
                  const SizedBox(height: AppSpacing.xs),
                  SelectableText(directory,
                      style: theme.textTheme.bodySmall?.copyWith(fontFamily: 'monospace')),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DryRunSummaryCard extends StatelessWidget {
  final DryRunSummary summary;
  const _DryRunSummaryCard({required this.summary});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.fact_check, color: theme.colorScheme.primary),
                const SizedBox(width: AppSpacing.sm),
                Expanded(child: Text('Dry-run validation results', style: theme.textTheme.titleMedium)),
              ],
            ),
            const SizedBox(height: AppSpacing.md),
            _ProgressStat(label: 'Supported files', value: summary.supportedFiles, color: status.good),
            _ProgressStat(label: 'Unsupported files', value: summary.unsupportedFiles, color: status.fair),
            _ProgressStat(label: 'Duplicates', value: summary.duplicates, color: status.poor),
            _ProgressStat(label: 'Total records', value: summary.totalRecords, color: theme.colorScheme.primary),
            const SizedBox(height: AppSpacing.lg),
            Text('Per-file details', style: theme.textTheme.labelLarge),
            const SizedBox(height: AppSpacing.sm),
            ...summary.results.map((r) => Padding(
                  padding: const EdgeInsets.only(bottom: AppSpacing.sm),
                  child: _ValidationTile(result: r),
                )),
          ],
        ),
      ),
    );
  }
}

class _ValidationTile extends StatelessWidget {
  final ValidationResultResponse result;
  const _ValidationTile({required this.result});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    final hasIssues = result.errors.isNotEmpty ||
        result.warnings.isNotEmpty ||
        result.unsupportedRecords.isNotEmpty ||
        result.duplicate;

    return Container(
      decoration: BoxDecoration(
        color: hasIssues ? status.fairContainer : status.goodContainer,
        borderRadius: BorderRadius.circular(AppRadius.md),
      ),
      clipBehavior: Clip.antiAlias,
      child: Theme(
        data: theme.copyWith(dividerColor: Colors.transparent),
        child: ExpansionTile(
          tilePadding: const EdgeInsets.symmetric(horizontal: AppSpacing.md, vertical: AppSpacing.xs),
          title: Row(
            children: [
              Icon(
                result.supported ? Icons.check_circle : Icons.cancel,
                color: result.supported ? status.good : status.poor,
                size: 20,
              ),
              const SizedBox(width: AppSpacing.sm),
              Expanded(child: Text(result.filename, style: theme.textTheme.bodyMedium)),
              if (result.duplicate)
                Padding(
                  padding: const EdgeInsets.only(left: AppSpacing.xs),
                  child: Icon(Icons.content_copy, size: 16, color: status.fair),
                ),
            ],
          ),
          subtitle: Text(
            '${result.detectedFormat.toUpperCase()} · ${result.recordCount} records'
            '${result.duplicate ? ' · DUPLICATE' : ''}',
            style: theme.textTheme.bodySmall,
          ),
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(AppSpacing.md, 0, AppSpacing.md, AppSpacing.md),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (result.contentHash != null) ...[
                    Text('Content hash (SHA-256):', style: theme.textTheme.labelMedium),
                    const SizedBox(height: AppSpacing.xs),
                    SelectableText(result.contentHash!,
                        style: theme.textTheme.bodySmall?.copyWith(fontFamily: 'monospace')),
                    const SizedBox(height: AppSpacing.sm),
                  ],
                  if (result.errors.isNotEmpty) ...[
                    Text('Errors:', style: theme.textTheme.labelMedium?.copyWith(color: status.poor)),
                    ...result.errors.map((e) => Text('• $e', style: theme.textTheme.bodySmall?.copyWith(color: status.poor))),
                    const SizedBox(height: AppSpacing.sm),
                  ],
                  if (result.warnings.isNotEmpty) ...[
                    Text('Warnings:', style: theme.textTheme.labelMedium?.copyWith(color: status.fair)),
                    ...result.warnings.map((w) => Text('• $w', style: theme.textTheme.bodySmall?.copyWith(color: status.fair))),
                    const SizedBox(height: AppSpacing.sm),
                  ],
                  if (result.unsupportedRecords.isNotEmpty) ...[
                    Text('Unsupported records:', style: theme.textTheme.labelMedium?.copyWith(color: status.fair)),
                    ...result.unsupportedRecords.map((u) => Text('• $u', style: theme.textTheme.bodySmall?.copyWith(color: status.fair))),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final String title;
  final int count;
  final IconData icon;
  final Color color;
  const _SectionHeader({required this.title, required this.count, required this.icon, required this.color});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, color: color, size: 20),
        const SizedBox(width: AppSpacing.sm),
        Text('$title ($count)', style: Theme.of(context).textTheme.titleMedium),
      ],
    );
  }
}

class _FileTile extends StatelessWidget {
  final ScannedFileResponse file;
  const _FileTile({required this.file});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    final sizeStr = _formatSize(file.sizeBytes);
    return Card(
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg, vertical: AppSpacing.xs),
        leading: Icon(
          file.supported ? Icons.check_circle : Icons.warning_amber,
          color: file.supported ? status.good : status.fair,
        ),
        title: Text(file.filename, style: theme.textTheme.bodyMedium),
        subtitle: Text('${file.detectedFormat.toUpperCase()} · $sizeStr', style: theme.textTheme.bodySmall),
        trailing: Text(
          file.supported ? 'Ready' : 'Unsupported',
          style: theme.textTheme.labelMedium?.copyWith(color: file.supported ? status.good : status.fair),
        ),
      ),
    );
  }

  String _formatSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }
}

class _PrivacyNoteCard extends StatelessWidget {
  const _PrivacyNoteCard();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    return Card(
      color: status.goodContainer,
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Row(
          children: [
            Icon(Icons.lock_outline, color: status.onGoodContainer),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Privacy-first import', style: theme.textTheme.labelLarge?.copyWith(color: status.onGoodContainer)),
                  const SizedBox(height: AppSpacing.xs),
                  Text(
                    'Files stay in the local import directory outside the repo. '
                    'Only checksums and metadata are stored in the database. '
                    'No data leaves your machine.',
                    style: theme.textTheme.bodySmall?.copyWith(color: status.onGoodContainer),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
