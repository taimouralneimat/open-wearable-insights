import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../widgets/state_views.dart';

/// Blood biomarker (lab bloodwork) import — docs/product/parity-matrix.md
/// row 22. Shows real readings imported from a CSV in the same local
/// import folder every other data source uses, grouped by category, each
/// checked against whatever reference range that reading's own CSV row
/// supplied (never a range this app invented — see the backend's
/// BiomarkerReferenceCatalog for why).
///
/// This screen — and everything on it — is for personal tracking only.
/// It is not a medical assessment or diagnosis. See [_DisclaimerBanner],
/// shown unconditionally regardless of state.
class BiomarkersPage extends StatefulWidget {
  const BiomarkersPage({super.key});

  @override
  State<BiomarkersPage> createState() => _BiomarkersPageState();
}

class _BiomarkersPageState extends State<BiomarkersPage> {
  final _apiClient = ApiClient();
  List<BiomarkerReadingResponse>? _readings;
  Map<String, String> _descriptionByName = {};
  String? _importDirectory;
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
      final readings = await _apiClient.getBiomarkerReadings();
      // Best-effort supplementary calls — a failure here shouldn't block
      // showing the real readings, which is the primary content.
      Map<String, String> descriptions = {};
      try {
        final reference = await _apiClient.getBiomarkerReference();
        descriptions = {for (final e in reference) e.name.toLowerCase(): e.description};
      } catch (_) {}
      String? importDir;
      try {
        importDir = (await _apiClient.scanImportDirectory()).directory;
      } catch (_) {}

      if (!mounted) return;
      setState(() {
        _readings = readings;
        _descriptionByName = descriptions;
        _importDirectory = importDir;
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
      appBar: AppBar(
        title: const Text('Blood biomarkers'),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), tooltip: 'Refresh', onPressed: _load),
        ],
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading) return const LoadingView(label: 'Loading biomarker readings…');
    if (_error != null) return ErrorView(title: "Couldn't load biomarker readings", message: _error!, onRetry: _load);

    final readings = _readings ?? [];
    if (readings.isEmpty) return _buildEmptyState();
    return _buildReadingsList(readings);
  }

  Widget _buildEmptyState() {
    return ListView(
      padding: const EdgeInsets.all(AppSpacing.lg),
      children: [
        const _DisclaimerBanner(),
        const SizedBox(height: AppSpacing.lg),
        Icon(Icons.science_outlined, size: 56, color: Theme.of(context).colorScheme.onSurfaceVariant),
        const SizedBox(height: AppSpacing.lg),
        Center(
          child: Text('No blood biomarkers imported yet',
              style: Theme.of(context).textTheme.titleLarge, textAlign: TextAlign.center),
        ),
        const SizedBox(height: AppSpacing.xl),
        _HowToImportCard(importDirectory: _importDirectory),
      ],
    );
  }

  Widget _buildReadingsList(List<BiomarkerReadingResponse> readings) {
    final grouped = _groupByCategoryThenName(readings);
    final categories = grouped.keys.toList()
      ..sort((a, b) {
        if (a == _uncategorized) return 1;
        if (b == _uncategorized) return -1;
        return a.compareTo(b);
      });

    return ListView(
      padding: const EdgeInsets.all(AppSpacing.lg),
      children: [
        const _DisclaimerBanner(),
        const SizedBox(height: AppSpacing.md),
        _HowToImportCard(importDirectory: _importDirectory, collapsedByDefault: true),
        const SizedBox(height: AppSpacing.lg),
        for (final category in categories) ...[
          _CategoryHeader(category: category),
          const SizedBox(height: AppSpacing.sm),
          for (final entry in grouped[category]!.entries)
            Padding(
              padding: const EdgeInsets.only(bottom: AppSpacing.sm),
              child: _BiomarkerCard(
                biomarkerName: entry.key,
                history: entry.value,
                description: _descriptionByName[entry.key.toLowerCase()],
              ),
            ),
          const SizedBox(height: AppSpacing.md),
        ],
      ],
    );
  }

  static const _uncategorized = 'Other';

  /// Groups readings by category, then by biomarker name, with each name's
  /// readings sorted most-recent-first (history sub-lists are re-sorted
  /// chronologically at render time for the trend view).
  Map<String, Map<String, List<BiomarkerReadingResponse>>> _groupByCategoryThenName(
      List<BiomarkerReadingResponse> readings) {
    final result = <String, Map<String, List<BiomarkerReadingResponse>>>{};
    for (final reading in readings) {
      final category = reading.category ?? _uncategorized;
      final byName = result.putIfAbsent(category, () => {});
      byName.putIfAbsent(reading.biomarkerName, () => []).add(reading);
    }
    for (final byName in result.values) {
      for (final list in byName.values) {
        list.sort((a, b) => b.readingDate.compareTo(a.readingDate));
      }
    }
    return result;
  }
}

/// Persistent "not medical advice" framing, matching the wording style this
/// project already uses for the readiness score and other wellness metrics
/// (e.g. "Not a medical measure"). Shown unconditionally on every state of
/// this page, not just when a reading happens to be out of range.
class _DisclaimerBanner extends StatelessWidget {
  const _DisclaimerBanner();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: status.infoContainer,
        borderRadius: BorderRadius.circular(AppRadius.md),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.info_outline, size: 18, color: status.onInfoContainer),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: Text(
              'For personal tracking only — not a medical assessment or diagnosis. '
              'Consult a healthcare professional to interpret lab results.',
              style: theme.textTheme.bodySmall?.copyWith(color: status.onInfoContainer),
            ),
          ),
        ],
      ),
    );
  }
}

class _HowToImportCard extends StatefulWidget {
  final String? importDirectory;
  final bool collapsedByDefault;
  const _HowToImportCard({required this.importDirectory, this.collapsedByDefault = false});

  @override
  State<_HowToImportCard> createState() => _HowToImportCardState();
}

class _HowToImportCardState extends State<_HowToImportCard> {
  late bool _expanded = !widget.collapsedByDefault;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dir = widget.importDirectory ?? '~/.open-wearable-insights/imports';

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            InkWell(
              onTap: () => setState(() => _expanded = !_expanded),
              child: Row(
                children: [
                  Expanded(child: Text('How to import blood biomarkers', style: theme.textTheme.titleMedium)),
                  Icon(_expanded ? Icons.expand_less : Icons.expand_more),
                ],
              ),
            ),
            if (_expanded) ...[
              const SizedBox(height: AppSpacing.md),
              Text('1. Prepare a CSV with these columns:', style: theme.textTheme.bodyMedium),
              const SizedBox(height: AppSpacing.xs),
              const _CodeBlock('date,biomarker_name,value,unit,reference_low,reference_high'),
              const SizedBox(height: AppSpacing.xs),
              const _CodeBlock('2026-04-15,LDL Cholesterol,95,mg/dL,0,100'),
              const SizedBox(height: AppSpacing.xs),
              Text(
                'reference_low/reference_high are optional — include them if your lab report shows them. '
                'An optional category column is also accepted.',
                style: theme.textTheme.bodySmall,
              ),
              const SizedBox(height: AppSpacing.md),
              Text('2. Place the file in your import folder:', style: theme.textTheme.bodyMedium),
              const SizedBox(height: AppSpacing.xs),
              _CodeBlock(dir),
              const SizedBox(height: AppSpacing.md),
              Text('3. Go to Import, then Dry-run and Import to load it.', style: theme.textTheme.bodyMedium),
              const SizedBox(height: AppSpacing.sm),
              Align(
                alignment: Alignment.centerLeft,
                child: OutlinedButton.icon(
                  onPressed: () => context.push('/import'),
                  icon: const Icon(Icons.download_outlined, size: 18),
                  label: const Text('Go to Import'),
                ),
              ),
            ],
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

class _CategoryHeader extends StatelessWidget {
  final String category;
  const _CategoryHeader({required this.category});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.xs),
      child: Text(category, style: Theme.of(context).textTheme.titleMedium),
    );
  }
}

/// One biomarker's card: latest reading prominent, with an expandable
/// history section (the "simple per-biomarker trend view") showing every
/// imported reading for this name, oldest first.
class _BiomarkerCard extends StatelessWidget {
  final String biomarkerName;
  final List<BiomarkerReadingResponse> history; // most-recent-first
  final String? description;

  const _BiomarkerCard({required this.biomarkerName, required this.history, this.description});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    final latest = history.first;
    final chronological = history.reversed.toList();

    final Color badgeColor;
    final String badgeLabel;
    if (latest.inRange == null) {
      badgeColor = theme.colorScheme.outline;
      badgeLabel = 'No reference range provided';
    } else if (latest.inRange!) {
      badgeColor = status.good;
      badgeLabel = 'In range';
    } else {
      badgeColor = status.poor;
      badgeLabel = 'Out of range';
    }

    return Card(
      clipBehavior: Clip.antiAlias,
      child: Theme(
        data: theme.copyWith(dividerColor: Colors.transparent),
        child: ExpansionTile(
          tilePadding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg, vertical: AppSpacing.xs),
          title: Text(biomarkerName, style: theme.textTheme.titleSmall),
          subtitle: Padding(
            padding: const EdgeInsets.only(top: 2),
            child: Row(
              children: [
                Text('${_formatValue(latest.value)} ${latest.unit}', style: theme.textTheme.bodyMedium),
                const SizedBox(width: AppSpacing.sm),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: AppSpacing.sm, vertical: 2),
                  decoration: BoxDecoration(
                    color: badgeColor.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(AppRadius.sm),
                  ),
                  child: Text(badgeLabel, style: theme.textTheme.labelSmall?.copyWith(color: badgeColor)),
                ),
              ],
            ),
          ),
          trailing: Text(latest.readingDate, style: theme.textTheme.bodySmall),
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(AppSpacing.lg, 0, AppSpacing.lg, AppSpacing.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (description != null) ...[
                    Text(description!, style: theme.textTheme.bodySmall),
                    const SizedBox(height: AppSpacing.sm),
                  ],
                  if (latest.hasReferenceRange) ...[
                    Text(_referenceRangeLabel(latest), style: theme.textTheme.bodySmall),
                    const SizedBox(height: AppSpacing.sm),
                  ],
                  Text('History (${chronological.length} reading${chronological.length == 1 ? '' : 's'})',
                      style: theme.textTheme.labelLarge),
                  const SizedBox(height: AppSpacing.xs),
                  for (final reading in chronological) _HistoryRow(reading: reading),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _referenceRangeLabel(BiomarkerReadingResponse r) {
    if (r.referenceLow != null && r.referenceHigh != null) {
      return 'Reference range from this reading: ${_formatValue(r.referenceLow!)}–${_formatValue(r.referenceHigh!)} ${r.unit}';
    }
    if (r.referenceLow != null) {
      return 'Reference range from this reading: above ${_formatValue(r.referenceLow!)} ${r.unit}';
    }
    return 'Reference range from this reading: below ${_formatValue(r.referenceHigh!)} ${r.unit}';
  }

  static String _formatValue(double v) {
    return v == v.roundToDouble() ? v.toStringAsFixed(0) : v.toString();
  }
}

class _HistoryRow extends StatelessWidget {
  final BiomarkerReadingResponse reading;
  const _HistoryRow({required this.reading});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    final Color dotColor = reading.inRange == null
        ? theme.colorScheme.outline
        : (reading.inRange! ? status.good : status.poor);

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        children: [
          Icon(Icons.circle, size: 8, color: dotColor),
          const SizedBox(width: AppSpacing.sm),
          Expanded(child: Text(reading.readingDate, style: theme.textTheme.bodySmall)),
          Text('${_BiomarkerCard._formatValue(reading.value)} ${reading.unit}', style: theme.textTheme.bodySmall),
        ],
      ),
    );
  }
}
