import 'package:flutter/material.dart';
import '../../app/app.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../widgets/state_views.dart';

/// Journal page — log self-reported behaviors and view past entries.
///
/// Entries are always treated as untrusted input (see backend
/// JournalService) — self-reported, never used to override a computed
/// score. Handles empty/loading/error/populated states.
class JournalPage extends StatefulWidget {
  const JournalPage({super.key});

  @override
  State<JournalPage> createState() => _JournalPageState();
}

class _JournalPageState extends State<JournalPage> {
  final _apiClient = ApiClient();
  List<BehaviorCategory>? _taxonomy;
  List<JournalEntryResponse>? _entries;
  List<BehaviorCorrelationResponse> _correlations = [];
  List<HabitStreakResponse> _streaks = [];
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
      final taxonomy = await _apiClient.getJournalBehaviors();
      final entries = await _apiClient.getJournalEntries();
      // Correlations are a nice-to-have enhancement, not core to the
      // journal working — don't fail the whole page if this call fails.
      List<BehaviorCorrelationResponse> correlations = [];
      try {
        correlations = await _apiClient.getCorrelations();
      } catch (_) {
        correlations = [];
      }
      List<HabitStreakResponse> streaks = [];
      try {
        streaks = await _apiClient.getHabitStreaks();
      } catch (_) {
        streaks = [];
      }
      setState(() {
        _taxonomy = taxonomy;
        _entries = entries;
        _correlations = correlations;
        _streaks = streaks;
        _loading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  Future<void> _openAddEntrySheet() async {
    if (_taxonomy == null) return;
    final added = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (context) => _AddEntrySheet(taxonomy: _taxonomy!, apiClient: _apiClient),
    );
    if (added == true) {
      _loadData();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Journal'),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), tooltip: 'Refresh', onPressed: _loadData),
          const MoreMenuButton(),
          const SizedBox(width: AppSpacing.xs),
        ],
      ),
      body: _buildBody(),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _taxonomy == null ? null : _openAddEntrySheet,
        icon: const Icon(Icons.add),
        label: const Text('Log entry'),
      ),
    );
  }

  Widget _buildBody() {
    if (_loading) return const LoadingView(label: 'Loading journal…');
    if (_error != null) {
      return ErrorView(title: 'Could not load journal', message: _error!, onRetry: _loadData);
    }
    final entries = _entries ?? [];
    if (entries.isEmpty) {
      return const EmptyView(
        icon: Icons.edit_note_outlined,
        title: 'No entries yet',
        message: 'Log behaviors like alcohol, stress, or recovery work\n'
            'to see them alongside your readiness over time.',
      );
    }
    return RefreshIndicator(
      onRefresh: _loadData,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(AppSpacing.lg, AppSpacing.lg, AppSpacing.lg, AppSpacing.xxxl + AppSpacing.lg),
        children: [
          if (_streaks.any((s) => s.currentStreak >= 2)) ...[
            _StreaksCard(streaks: _streaks.where((s) => s.currentStreak >= 2).toList()),
            const SizedBox(height: AppSpacing.lg),
          ],
          if (_correlations.isNotEmpty) ...[
            _CorrelationsCard(correlations: _correlations),
            const SizedBox(height: AppSpacing.lg),
          ],
          ...entries.map((e) => Padding(
                padding: const EdgeInsets.only(bottom: AppSpacing.sm),
                child: _EntryTile(entry: e),
              )),
        ],
      ),
    );
  }
}

/// "Don't break the chain" — consecutive-day streaks for behaviors actually
/// being kept up. Only shows streaks of 2+ days; a single log isn't a
/// streak yet.
class _StreaksCard extends StatelessWidget {
  final List<HabitStreakResponse> streaks;
  const _StreaksCard({required this.streaks});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    return Card(
      color: status.goodContainer,
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.local_fire_department, size: 20, color: status.onGoodContainer),
                const SizedBox(width: AppSpacing.sm),
                Text('Streaks', style: theme.textTheme.titleMedium?.copyWith(color: status.onGoodContainer)),
              ],
            ),
            const SizedBox(height: AppSpacing.md),
            Wrap(
              spacing: AppSpacing.sm,
              runSpacing: AppSpacing.sm,
              children: streaks.map((s) {
                return Chip(
                  avatar: Icon(Icons.local_fire_department, size: 16, color: status.onGoodContainer),
                  label: Text('${s.behavior} · ${s.currentStreak}d'),
                  backgroundColor: status.good.withValues(alpha: 0.15),
                  labelStyle: theme.textTheme.labelLarge?.copyWith(color: status.onGoodContainer),
                  side: BorderSide.none,
                );
              }).toList(),
            ),
          ],
        ),
      ),
    );
  }
}

class _CorrelationsCard extends StatelessWidget {
  final List<BehaviorCorrelationResponse> correlations;
  const _CorrelationsCard({required this.correlations});

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
                Icon(Icons.insights, size: 20, color: status.onInfoContainer),
                const SizedBox(width: AppSpacing.sm),
                Text('Patterns worth watching',
                    style: theme.textTheme.titleMedium?.copyWith(color: status.onInfoContainer)),
              ],
            ),
            const SizedBox(height: AppSpacing.xs),
            Text(
              'Correlation, not causation — many other factors vary day to day too.',
              style: theme.textTheme.bodySmall?.copyWith(color: status.onInfoContainer, fontStyle: FontStyle.italic),
            ),
            const SizedBox(height: AppSpacing.md),
            ...correlations.map((c) => _CorrelationRow(correlation: c)),
          ],
        ),
      ),
    );
  }
}

class _CorrelationRow extends StatelessWidget {
  final BehaviorCorrelationResponse correlation;
  const _CorrelationRow({required this.correlation});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    final worse = correlation.difference < 0;
    final color = worse ? status.poor : status.good;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.sm),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(worse ? Icons.trending_down : Icons.trending_up, size: 16, color: color),
              const SizedBox(width: AppSpacing.sm),
              Expanded(
                child: Text(
                  '${correlation.behavior}: readiness averaged '
                  '${correlation.avgReadinessWhenLogged.toStringAsFixed(0)} on logged days vs. '
                  '${correlation.avgReadinessWhenNotLogged.toStringAsFixed(0)} otherwise',
                  style: theme.textTheme.bodyMedium?.copyWith(color: status.onInfoContainer),
                ),
              ),
            ],
          ),
          Padding(
            padding: const EdgeInsets.only(left: 24, top: 2),
            child: Text(
              '${correlation.loggedDayCount} logged / ${correlation.notLoggedDayCount} not logged · '
              '${correlation.confidence} confidence',
              style: theme.textTheme.bodySmall?.copyWith(color: status.onInfoContainer.withValues(alpha: 0.75)),
            ),
          ),
        ],
      ),
    );
  }
}

class _EntryTile extends StatelessWidget {
  final JournalEntryResponse entry;
  const _EntryTile({required this.entry});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg, vertical: AppSpacing.xs),
        leading: Icon(Icons.edit_note, color: theme.colorScheme.primary),
        title: Text(entry.behavior, style: theme.textTheme.titleMedium),
        subtitle: Text(
          [
            entry.category,
            if (entry.value != null && entry.value!.isNotEmpty) entry.value,
            if (entry.note != null && entry.note!.isNotEmpty) entry.note,
          ].join(' · '),
          style: theme.textTheme.bodyMedium,
        ),
        trailing: Text(entry.time.substring(0, 10), style: theme.textTheme.bodySmall),
      ),
    );
  }
}

class _AddEntrySheet extends StatefulWidget {
  final List<BehaviorCategory> taxonomy;
  final ApiClient apiClient;
  const _AddEntrySheet({required this.taxonomy, required this.apiClient});

  @override
  State<_AddEntrySheet> createState() => _AddEntrySheetState();
}

class _AddEntrySheetState extends State<_AddEntrySheet> {
  late BehaviorCategory _selectedCategory;
  String? _selectedBehavior;
  final _valueController = TextEditingController();
  final _noteController = TextEditingController();
  bool _submitting = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _selectedCategory = widget.taxonomy.first;
  }

  Future<void> _submit() async {
    if (_selectedBehavior == null) {
      setState(() => _error = 'Pick a behavior first.');
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await widget.apiClient.addJournalEntry(
        category: _selectedCategory.name,
        behavior: _selectedBehavior!,
        value: _valueController.text.isEmpty ? null : _valueController.text,
        note: _noteController.text.isEmpty ? null : _noteController.text,
      );
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      setState(() {
        _submitting = false;
        _error = 'Could not save: $e';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: EdgeInsets.only(
        left: AppSpacing.lg,
        right: AppSpacing.lg,
        top: AppSpacing.lg,
        bottom: MediaQuery.of(context).viewInsets.bottom + AppSpacing.lg,
      ),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Center(
              child: Container(
                width: 36,
                height: 4,
                margin: const EdgeInsets.only(bottom: AppSpacing.lg),
                decoration: BoxDecoration(
                  color: theme.colorScheme.outlineVariant,
                  borderRadius: BorderRadius.circular(AppRadius.pill),
                ),
              ),
            ),
            Text('Log a behavior', style: theme.textTheme.titleLarge),
            const SizedBox(height: AppSpacing.lg),
            DropdownButtonFormField<BehaviorCategory>(
              initialValue: _selectedCategory,
              decoration: const InputDecoration(labelText: 'Category'),
              items: widget.taxonomy
                  .map((c) => DropdownMenuItem(value: c, child: Text(c.name)))
                  .toList(),
              onChanged: (c) {
                if (c == null) return;
                setState(() {
                  _selectedCategory = c;
                  _selectedBehavior = null;
                });
              },
            ),
            const SizedBox(height: AppSpacing.md),
            Wrap(
              spacing: AppSpacing.sm,
              runSpacing: AppSpacing.sm,
              children: _selectedCategory.behaviors.map((b) {
                return ChoiceChip(
                  label: Text(b),
                  selected: _selectedBehavior == b,
                  onSelected: (_) => setState(() => _selectedBehavior = b),
                );
              }).toList(),
            ),
            const SizedBox(height: AppSpacing.md),
            TextField(
              controller: _valueController,
              decoration: const InputDecoration(labelText: 'Value (optional, e.g. "2 units", "10 min")'),
            ),
            const SizedBox(height: AppSpacing.sm),
            TextField(
              controller: _noteController,
              decoration: const InputDecoration(labelText: 'Note (optional)'),
            ),
            if (_error != null) ...[
              const SizedBox(height: AppSpacing.sm),
              Text(_error!, style: theme.textTheme.bodySmall?.copyWith(color: theme.status.poor)),
            ],
            const SizedBox(height: AppSpacing.lg),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: _submitting ? null : _submit,
                child: _submitting
                    ? const SizedBox(height: 16, width: 16, child: CircularProgressIndicator(strokeWidth: 2))
                    : const Text('Save entry'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
