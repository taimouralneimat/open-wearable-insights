import 'package:flutter/material.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
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
  bool _loading = true;
  bool _saving = false;
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
      if (!mounted) return;
      setState(() {
        _profile = profile;
        _goalOptions = goals;
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
      if (!mounted) return;
      setState(() {
        _profile = updated;
        _saving = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Profile saved')));
    } catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Failed to save: $e')));
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
          "Shapes what we emphasize in the coach's explanations over time — not used yet, just captured for now.",
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
