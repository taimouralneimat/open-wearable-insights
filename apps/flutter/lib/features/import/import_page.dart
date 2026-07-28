import 'package:flutter/material.dart';
import '../../data/api_client.dart';

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
  bool _loading = true;
  bool _validating = false;
  bool _importing = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _scanDirectory();
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
        title: const Text('Import Data'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Rescan',
            onPressed: _scanDirectory,
          ),
        ],
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading) return _buildLoading();
    if (_error != null) return _buildError();
    if (_scanResult == null) return _buildEmpty();
    if (!_scanResult!.exists) return _buildDirectoryMissing();
    if (_scanResult!.files.isEmpty) return _buildNoFiles();
    return _buildFileList();
  }

  Widget _buildLoading() {
    return const Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          CircularProgressIndicator(),
          SizedBox(height: 16),
          Text('Scanning import directory...'),
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
          const Text('Couldn\'t scan import directory',
              style: TextStyle(fontSize: 20)),
          const SizedBox(height: 8),
          Text(_error!,
              style: const TextStyle(color: Colors.grey),
              textAlign: TextAlign.center),
          const SizedBox(height: 16),
          ElevatedButton(
              onPressed: _scanDirectory, child: const Text('Retry')),
        ],
      ),
    );
  }

  Widget _buildEmpty() {
    return const Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.folder_off_outlined, size: 64, color: Colors.grey),
          SizedBox(height: 16),
          Text('No scan result', style: TextStyle(fontSize: 20)),
        ],
      ),
    );
  }

  Widget _buildDirectoryMissing() {
    final dir = _scanResult?.directory ?? 'unknown';
    final msg = _scanResult?.errorMessage ?? 'Directory does not exist.';
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.folder_off_outlined,
                size: 64, color: Colors.orange),
            const SizedBox(height: 16),
            const Text('Import directory not found',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.w500)),
            const SizedBox(height: 8),
            Text(msg,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.grey)),
            const SizedBox(height: 24),
            _buildGuidanceCard(dir),
          ],
        ),
      ),
    );
  }

  Widget _buildGuidanceCard(String dir) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('How to import data',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
            const SizedBox(height: 12),
            const Text('1. Create the import directory outside the repo:'),
            const SizedBox(height: 4),
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: Colors.grey.shade100,
                borderRadius: BorderRadius.circular(4),
              ),
              child: SelectableText(
                'mkdir -p "$dir"',
                style: const TextStyle(fontFamily: 'monospace'),
              ),
            ),
            const SizedBox(height: 12),
            const Text('2. Place your Garmin export files there:'),
            const SizedBox(height: 4),
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: Colors.grey.shade100,
                borderRadius: BorderRadius.circular(4),
              ),
              child: SelectableText(
                'cp ~/Downloads/garmin-export/* "$dir"/',
                style: const TextStyle(fontFamily: 'monospace'),
              ),
            ),
            const SizedBox(height: 12),
            const Text('3. Click rescan to detect your files.'),
            const SizedBox(height: 8),
            const Row(
              children: [
                Icon(Icons.lock_outline, size: 16, color: Colors.green),
                SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Real health data stays outside the repo. '
                    'Only checksums and metadata are stored in the database.',
                    style: TextStyle(color: Colors.grey, fontSize: 12),
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
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.folder_open_outlined,
                size: 64, color: Colors.grey),
            const SizedBox(height: 16),
            const Text('No files found',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.w500)),
            const SizedBox(height: 8),
            Text('Place your export files in:\n$dir',
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.grey)),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _scanDirectory,
              child: const Text('Rescan'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFileList() {
    final result = _scanResult!;
    final supported = result.files.where((f) => f.supported).toList();
    final unsupported = result.files.where((f) => !f.supported).toList();

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _DirectoryInfoCard(directory: result.directory),
        const SizedBox(height: 16),
        // Action buttons
        Row(
          children: [
            Expanded(
              child: ElevatedButton.icon(
                onPressed: _validating ? null : _runDryRun,
                icon: _validating
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.fact_check_outlined),
                label: Text(_validating ? 'Validating...' : 'Dry-Run'),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: ElevatedButton.icon(
                onPressed: _importing ? null : _runImport,
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.green,
                  foregroundColor: Colors.white,
                ),
                icon: _importing
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.download_outlined),
                label: Text(_importing ? 'Importing...' : 'Import'),
              ),
            ),
          ],
        ),
        if (_dryRunResult != null) ...[
          const SizedBox(height: 16),
          _DryRunSummaryCard(summary: _dryRunResult!),
        ],
        if (_importProgress != null) ...[
          const SizedBox(height: 16),
          _ImportProgressCard(progress: _importProgress!),
        ],
        if (_batches.isNotEmpty) ...[
          const SizedBox(height: 16),
          _BatchesCard(
            batches: _batches,
            onUndo: _undoBatch,
          ),
        ],
        const SizedBox(height: 16),
        if (supported.isNotEmpty) ...[
          _SectionHeader(
            title: 'Supported Files',
            count: supported.length,
            icon: Icons.check_circle,
            color: Colors.green,
          ),
          const SizedBox(height: 8),
          ...supported.map((f) => _FileTile(file: f)),
          const SizedBox(height: 16),
        ],
        if (unsupported.isNotEmpty) ...[
          _SectionHeader(
            title: 'Unsupported Files',
            count: unsupported.length,
            icon: Icons.warning_amber,
            color: Colors.orange,
          ),
          const SizedBox(height: 8),
          ...unsupported.map((f) => _FileTile(file: f)),
          const SizedBox(height: 16),
        ],
        const _PrivacyNoteCard(),
      ],
    );
  }
}

class _ImportProgressCard extends StatelessWidget {
  final ImportProgress progress;
  const _ImportProgressCard({required this.progress});

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Colors.blue.shade50,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Row(
              children: [
                Icon(Icons.download_done, color: Colors.blue),
                SizedBox(width: 8),
                Text('Import Results',
                    style:
                        TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
              ],
            ),
            const SizedBox(height: 12),
            _ProgressStat(label: 'Imported', value: progress.imported, color: Colors.green),
            _ProgressStat(label: 'Skipped (duplicates/unsupported)', value: progress.skipped, color: Colors.orange),
            _ProgressStat(label: 'Failed', value: progress.failed, color: Colors.red),
            _ProgressStat(label: 'Total records', value: progress.totalRecords, color: Colors.blue),
            const SizedBox(height: 12),
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
  const _ProgressStat({
    required this.label,
    required this.value,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        children: [
          Text(label, style: const TextStyle(color: Colors.grey)),
          const Spacer(),
          Text('$value',
              style: TextStyle(color: color, fontWeight: FontWeight.w600)),
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
    final color = result.status == 'imported'
        ? Colors.green
        : result.status == 'duplicate' || result.status == 'skipped'
            ? Colors.orange
            : Colors.red;
    final icon = result.status == 'imported'
        ? Icons.check_circle
        : result.status == 'duplicate'
            ? Icons.content_copy
            : result.status == 'skipped'
                ? Icons.skip_next
                : Icons.error;
    final statusText = result.status;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Icon(icon, color: color, size: 20),
          const SizedBox(width: 8),
          Expanded(child: Text(result.filename)),
          Text(statusText,
              style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w500)),
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
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Row(
              children: [
                Icon(Icons.history, color: Colors.blue),
                SizedBox(width: 8),
                Text('Import History',
                    style:
                        TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
              ],
            ),
            const SizedBox(height: 12),
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
    final isUndone = batch.status == 'undone';
    final statusText = isUndone ? 'Undone' : 'Undo';
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Icon(
            isUndone ? Icons.undo : Icons.check_circle,
            color: isUndone ? Colors.grey : Colors.green,
            size: 20,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(batch.fileName ?? 'Unknown file',
                    style: const TextStyle(fontSize: 13)),
                Text(
                  '${batch.source.toUpperCase()} • ${batch.recordCount} records • ${batch.importedAt.substring(0, 19)}',
                  style: const TextStyle(color: Colors.grey, fontSize: 11),
                ),
              ],
            ),
          ),
          if (!isUndone)
            TextButton(
              onPressed: () => onUndo(batch.id),
              child: Text(statusText, style: const TextStyle(color: Colors.orange)),
            ),
          if (isUndone)
            Text(statusText,
                style: const TextStyle(color: Colors.grey, fontSize: 12)),
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
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            const Icon(Icons.folder, color: Colors.blue),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Import Directory',
                      style: TextStyle(fontWeight: FontWeight.w500)),
                  const SizedBox(height: 4),
                  SelectableText(directory,
                      style: const TextStyle(
                          color: Colors.grey, fontFamily: 'monospace')),
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
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.fact_check, color: Colors.blue),
                const SizedBox(width: 8),
                const Text('Dry-Run Validation Results',
                    style:
                        TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
                const Spacer(),
                TextButton(
                  onPressed: () {},
                  child: const Text('Re-run'),
                ),
              ],
            ),
            const SizedBox(height: 12),
            _SummaryStat(
                label: 'Supported files', value: summary.supportedFiles, color: Colors.green),
            _SummaryStat(
                label: 'Unsupported files', value: summary.unsupportedFiles, color: Colors.orange),
            _SummaryStat(
                label: 'Duplicates', value: summary.duplicates, color: Colors.red),
            _SummaryStat(
                label: 'Total records', value: summary.totalRecords, color: Colors.blue),
            const SizedBox(height: 16),
            const Text('Per-File Details',
                style: TextStyle(fontWeight: FontWeight.w500)),
            const SizedBox(height: 8),
            ...summary.results.map((r) => _ValidationTile(result: r)),
          ],
        ),
      ),
    );
  }
}

class _SummaryStat extends StatelessWidget {
  final String label;
  final int value;
  final Color color;
  const _SummaryStat({
    required this.label,
    required this.value,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        children: [
          Text(label, style: const TextStyle(color: Colors.grey)),
          const Spacer(),
          Text('$value',
              style: TextStyle(color: color, fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}

class _ValidationTile extends StatelessWidget {
  final ValidationResultResponse result;
  const _ValidationTile({required this.result});

  @override
  Widget build(BuildContext context) {
    final hasIssues = result.errors.isNotEmpty ||
        result.warnings.isNotEmpty ||
        result.unsupportedRecords.isNotEmpty ||
        result.duplicate;

    return Card(
      color: hasIssues ? Colors.orange.shade50 : Colors.green.shade50,
      child: ExpansionTile(
        tilePadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
        title: Row(
          children: [
            Icon(
              result.supported ? Icons.check_circle : Icons.cancel,
              color: result.supported ? Colors.green : Colors.red,
              size: 20,
            ),
            const SizedBox(width: 8),
            Expanded(child: Text(result.filename)),
            if (result.duplicate)
              const Padding(
                padding: EdgeInsets.only(left: 4),
                child: Icon(Icons.content_copy, size: 16, color: Colors.orange),
              ),
          ],
        ),
        subtitle: Text(
          '${result.detectedFormat.toUpperCase()} • ${result.recordCount} records'
          '${result.duplicate ? ' • DUPLICATE' : ''}',
          style: TextStyle(
            color: result.duplicate ? Colors.orange : Colors.grey,
            fontSize: 12,
          ),
        ),
        children: [
          Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (result.contentHash != null) ...[
                  const Text('Content hash (SHA-256):',
                      style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500)),
                  const SizedBox(height: 4),
                  SelectableText(result.contentHash!,
                      style: const TextStyle(
                          fontSize: 10, fontFamily: 'monospace', color: Colors.grey)),
                  const SizedBox(height: 8),
                ],
                if (result.errors.isNotEmpty) ...[
                  const Text('Errors:',
                      style: TextStyle(
                          color: Colors.red, fontWeight: FontWeight.w500)),
                  ...result.errors.map((e) => Padding(
                        padding: const EdgeInsets.only(left: 8),
                        child: Text('• $e',
                            style: const TextStyle(color: Colors.red, fontSize: 12)),
                      )),
                  const SizedBox(height: 8),
                ],
                if (result.warnings.isNotEmpty) ...[
                  const Text('Warnings:',
                      style: TextStyle(
                          color: Colors.orange, fontWeight: FontWeight.w500)),
                  ...result.warnings.map((w) => Padding(
                        padding: const EdgeInsets.only(left: 8),
                        child: Text('• $w',
                            style: const TextStyle(color: Colors.orange, fontSize: 12)),
                      )),
                  const SizedBox(height: 8),
                ],
                if (result.unsupportedRecords.isNotEmpty) ...[
                  const Text('Unsupported records:',
                      style: TextStyle(
                          color: Colors.orange, fontWeight: FontWeight.w500)),
                  ...result.unsupportedRecords.map((u) => Padding(
                        padding: const EdgeInsets.only(left: 8),
                        child: Text('• $u',
                            style: const TextStyle(color: Colors.orange, fontSize: 12)),
                      )),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final String title;
  final int count;
  final IconData icon;
  final Color color;
  const _SectionHeader({
    required this.title,
    required this.count,
    required this.icon,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, color: color, size: 20),
        const SizedBox(width: 8),
        Text('$title ($count)',
            style:
                const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
      ],
    );
  }
}

class _FileTile extends StatelessWidget {
  final ScannedFileResponse file;
  const _FileTile({required this.file});

  @override
  Widget build(BuildContext context) {
    final sizeStr = _formatSize(file.sizeBytes);
    return Card(
      child: ListTile(
        leading: Icon(
          file.supported ? Icons.check_circle : Icons.warning_amber,
          color: file.supported ? Colors.green : Colors.orange,
        ),
        title: Text(file.filename),
        subtitle: Text('${file.detectedFormat.toUpperCase()} • $sizeStr'),
        trailing: file.supported
            ? const Text('Ready', style: TextStyle(color: Colors.green))
            : const Text('Unsupported',
                style: TextStyle(color: Colors.orange)),
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
    return Card(
      color: Colors.green.shade50,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            const Icon(Icons.lock_outline, color: Colors.green),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Privacy-first import',
                      style: TextStyle(fontWeight: FontWeight.w500)),
                  const SizedBox(height: 4),
                  Text(
                    'Files stay in the local import directory outside the repo. '
                    'Only checksums and metadata are stored in the database. '
                    'No data leaves your machine.',
                    style: TextStyle(
                        color: Colors.green.shade800, fontSize: 12),
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
