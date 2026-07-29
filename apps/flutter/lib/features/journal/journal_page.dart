import 'package:flutter/material.dart';
import '../../data/api_client.dart';

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
      setState(() {
        _taxonomy = taxonomy;
        _entries = entries;
        _correlations = correlations;
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
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: _loadData,
          ),
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
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_error != null) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error_outline, size: 64, color: Colors.red),
            const SizedBox(height: 16),
            Text('Could not load journal: $_error',
                style: const TextStyle(color: Colors.grey), textAlign: TextAlign.center),
            const SizedBox(height: 16),
            ElevatedButton(onPressed: _loadData, child: const Text('Retry')),
          ],
        ),
      );
    }
    final entries = _entries ?? [];
    if (entries.isEmpty) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.edit_note, size: 64, color: Colors.grey),
              SizedBox(height: 16),
              Text('No entries yet', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w500)),
              SizedBox(height: 8),
              Text('Log behaviors like alcohol, stress, or recovery work\n'
                  'to see them alongside your readiness over time.',
                  textAlign: TextAlign.center, style: TextStyle(color: Colors.grey)),
            ],
          ),
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: _loadData,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (_correlations.isNotEmpty) ...[
            _CorrelationsCard(correlations: _correlations),
            const SizedBox(height: 16),
          ],
          ...entries.map((e) => _EntryTile(entry: e)),
        ],
      ),
    );
  }
}

class _CorrelationsCard extends StatelessWidget {
  final List<BehaviorCorrelationResponse> correlations;
  const _CorrelationsCard({required this.correlations});

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Colors.blue.shade50,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.insights, size: 20, color: Colors.blue.shade800),
                const SizedBox(width: 8),
                Text('Patterns worth watching',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: Colors.blue.shade800)),
              ],
            ),
            const SizedBox(height: 4),
            Text(
              'Correlation, not causation — many other factors vary day to day too.',
              style: TextStyle(fontSize: 12, color: Colors.blue.shade900, fontStyle: FontStyle.italic),
            ),
            const SizedBox(height: 12),
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
    final worse = correlation.difference < 0;
    final color = worse ? Colors.red : Colors.green;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(worse ? Icons.trending_down : Icons.trending_up, size: 16, color: color),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  '${correlation.behavior}: readiness averaged '
                  '${correlation.avgReadinessWhenLogged.toStringAsFixed(0)} on logged days vs. '
                  '${correlation.avgReadinessWhenNotLogged.toStringAsFixed(0)} otherwise',
                  style: const TextStyle(fontSize: 13),
                ),
              ),
            ],
          ),
          Padding(
            padding: const EdgeInsets.only(left: 22),
            child: Text(
              '${correlation.loggedDayCount} logged / ${correlation.notLoggedDayCount} not logged · '
              '${correlation.confidence} confidence',
              style: const TextStyle(fontSize: 11, color: Colors.grey),
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
    return Card(
      child: ListTile(
        leading: const Icon(Icons.edit_note),
        title: Text(entry.behavior),
        subtitle: Text([
          entry.category,
          if (entry.value != null && entry.value!.isNotEmpty) entry.value,
          if (entry.note != null && entry.note!.isNotEmpty) entry.note,
        ].join(' · ')),
        trailing: Text(
          entry.time.substring(0, 10),
          style: const TextStyle(color: Colors.grey, fontSize: 12),
        ),
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
    return Padding(
      padding: EdgeInsets.only(
        left: 16, right: 16, top: 16,
        bottom: MediaQuery.of(context).viewInsets.bottom + 16,
      ),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Log a behavior', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
            const SizedBox(height: 16),
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
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: _selectedCategory.behaviors.map((b) {
                return ChoiceChip(
                  label: Text(b),
                  selected: _selectedBehavior == b,
                  onSelected: (_) => setState(() => _selectedBehavior = b),
                );
              }).toList(),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _valueController,
              decoration: const InputDecoration(labelText: 'Value (optional, e.g. "2 units", "10 min")'),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _noteController,
              decoration: const InputDecoration(labelText: 'Note (optional)'),
            ),
            if (_error != null) ...[
              const SizedBox(height: 8),
              Text(_error!, style: const TextStyle(color: Colors.red, fontSize: 12)),
            ],
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
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
