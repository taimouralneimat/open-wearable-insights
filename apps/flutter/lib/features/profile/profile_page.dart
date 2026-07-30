import 'package:flutter/material.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../data/web_download.dart';
import '../../widgets/state_views.dart';

/// Profile page — the local account's display name and primary goal.
///
/// Previously there was no concept of "who this is" anywhere in the app —
/// every screen just read account_id=1 with nothing personal behind it.
class ProfilePage extends StatefulWidget {
  const ProfilePage({super.key});

  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
  final _apiClient = ApiClient();
  ProfileResponse? _profile;
  List<String> _goalOptions = [];
  IdentityVotesResponse? _votes;
  bool _loading = true;
  bool _saving = false;
  bool _exporting = false;
  String? _error;

  final _nameController = TextEditingController();
  String? _selectedGoal;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final profile = await _apiClient.getProfile();
      final goals = await _apiClient.getGoalOptions();
      // Best-effort — votes are a secondary surface, shouldn't block the
      // profile itself from loading.
      IdentityVotesResponse? votes;
      try { votes = await _apiClient.getIdentityVotes(); } catch (_) { votes = null; }
      if (!mounted) return;
      setState(() {
        _profile = profile;
        _goalOptions = goals;
        _votes = votes;
        _nameController.text = profile.displayName ?? '';
        _selectedGoal = profile.primaryGoal;
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

  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      final updated = await _apiClient.updateProfile(
        displayName: _nameController.text.trim().isEmpty ? null : _nameController.text.trim(),
        primaryGoal: _selectedGoal,
      );
      IdentityVotesResponse? votes;
      try { votes = await _apiClient.getIdentityVotes(); } catch (_) { votes = null; }
      if (!mounted) return;
      setState(() {
        _profile = updated;
        _votes = votes;
        _saving = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Profile saved')));
    } catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Failed to save: $e')));
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
      appBar: AppBar(title: const Text('Profile')),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading) return const LoadingView(label: 'Loading profile…');
    if (_error != null) return ErrorView(message: _error!, onRetry: _load);
    final profile = _profile;
    if (profile == null) return const EmptyView(icon: Icons.person_outline, title: 'Profile not found');

    final theme = Theme.of(context);
    return ListView(
      padding: const EdgeInsets.all(AppSpacing.lg),
      children: [
        Center(
          child: CircleAvatar(
            radius: 40,
            backgroundColor: theme.colorScheme.primaryContainer,
            child: Text(
              _initials(profile.displayName, profile.email),
              style: theme.textTheme.headlineSmall?.copyWith(color: theme.colorScheme.onPrimaryContainer),
            ),
          ),
        ),
        const SizedBox(height: AppSpacing.xxl),
        Text('Display name', style: theme.textTheme.titleMedium),
        const SizedBox(height: AppSpacing.sm),
        TextField(
          controller: _nameController,
          decoration: const InputDecoration(hintText: 'What should we call you?'),
        ),
        const SizedBox(height: AppSpacing.xl),
        Text('Primary goal', style: theme.textTheme.titleMedium),
        const SizedBox(height: AppSpacing.sm),
        Text(
          'Every journal entry in a relevant category counts as a vote toward becoming '
          "this kind of person — see it below once it's set.",
          style: theme.textTheme.bodySmall,
        ),
        const SizedBox(height: AppSpacing.md),
        Wrap(
          spacing: AppSpacing.sm,
          runSpacing: AppSpacing.sm,
          children: _goalOptions.map((goal) {
            final selected = goal == _selectedGoal;
            return ChoiceChip(
              label: Text(goal),
              selected: selected,
              onSelected: (_) => setState(() => _selectedGoal = selected ? null : goal),
            );
          }).toList(),
        ),
        if (_votes != null && _votes!.goal != null) ...[
          const SizedBox(height: AppSpacing.lg),
          _IdentityVotesCard(votes: _votes!),
        ],
        const SizedBox(height: AppSpacing.xxl),
        Text('Account', style: theme.textTheme.titleMedium),
        const SizedBox(height: AppSpacing.sm),
        Text(profile.email, style: theme.textTheme.bodyMedium),
        const SizedBox(height: AppSpacing.xxl),
        FilledButton(
          onPressed: _saving ? null : _save,
          child: _saving
              ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Save'),
        ),
        const SizedBox(height: AppSpacing.xxl),
        Text('Your data', style: theme.textTheme.titleMedium),
        const SizedBox(height: AppSpacing.sm),
        Text(
          'Download everything stored locally for this account — measurements, activities, '
          'journal entries, readiness history, and more — as a single JSON file.',
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

  String _initials(String? name, String email) {
    final source = (name != null && name.trim().isNotEmpty) ? name.trim() : email;
    final parts = source.split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
    if (parts.isEmpty) return '?';
    if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase();
    return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
  }
}

/// Identity-based habit framing (Atomic Habits): every relevant logged
/// entry is a "vote" for becoming this kind of person, not just a raw
/// activity count.
class _IdentityVotesCard extends StatelessWidget {
  final IdentityVotesResponse votes;
  const _IdentityVotesCard({required this.votes});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: theme.colorScheme.tertiaryContainer,
        borderRadius: BorderRadius.circular(AppRadius.md),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.how_to_vote_outlined, color: theme.colorScheme.onTertiaryContainer),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '${votes.votes} vote${votes.votes == 1 ? '' : 's'} in the last ${votes.windowDays} days',
                  style: theme.textTheme.titleMedium?.copyWith(color: theme.colorScheme.onTertiaryContainer),
                ),
                const SizedBox(height: AppSpacing.xs),
                Text(
                  'Toward becoming someone who prioritizes "${votes.goal}" — counted from '
                  '${votes.relevantCategories.join(", ")} entries (${votes.totalEntries} total logged in the window).',
                  style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onTertiaryContainer),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
