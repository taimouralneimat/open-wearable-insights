import 'package:flutter/material.dart';
import 'package:open_wearable_insights/app/theme.dart';
import 'package:open_wearable_insights/data/api_client.dart';

/// Celebrates genuine personal records — an original delight feature, not
/// competitor parity. Every entry is a real comparison against the
/// account's own historical data already computed elsewhere in this app
/// (see MilestoneService on the backend); never a fabricated or generic
/// "you're doing great!" filler.
///
/// Fetches its own data (same self-contained pattern as the dashboard's
/// other lightweight entry cards, e.g. the healthspan/monthly-report
/// cards) and renders nothing at all when there are no real milestones
/// right now — same "render nothing when there's nothing to show"
/// convention as ConfidenceBanner/LlmBadge — so this is safe to include
/// unconditionally on the dashboard. Bakes in its own top margin (like
/// ConfidenceBanner's own bottom padding) so a caller never needs to wrap
/// it in a conditional SizedBox to avoid a stray gap when it's empty.
class MilestonesCard extends StatefulWidget {
  const MilestonesCard({super.key});

  @override
  State<MilestonesCard> createState() => _MilestonesCardState();
}

class _MilestonesCardState extends State<MilestonesCard> {
  final _apiClient = ApiClient();
  List<MilestoneResponse>? _milestones;

  @override
  void initState() {
    super.initState();
    // Best-effort, like the other dashboard entry cards — a failed fetch
    // just means this secondary delight card doesn't show, never a
    // dashboard-wide error.
    _apiClient.getMilestones().then((r) {
      if (mounted) setState(() => _milestones = r.milestones);
    }).catchError((_) {});
  }

  @override
  Widget build(BuildContext context) {
    final milestones = _milestones;
    if (milestones == null || milestones.isEmpty) return const SizedBox.shrink();

    final theme = Theme.of(context);
    final status = theme.status;

    return Padding(
      padding: const EdgeInsets.only(top: AppSpacing.lg),
      child: Card(
        color: status.goodContainer,
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(Icons.emoji_events_outlined, color: status.onGoodContainer),
                  const SizedBox(width: AppSpacing.sm),
                  Text(
                    milestones.length == 1 ? 'New personal record' : 'New personal records',
                    style: theme.textTheme.titleMedium?.copyWith(color: status.onGoodContainer),
                  ),
                ],
              ),
              const SizedBox(height: AppSpacing.md),
              for (final m in milestones)
                Padding(
                  padding: const EdgeInsets.only(bottom: AppSpacing.sm),
                  child: _MilestoneRow(milestone: m),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _MilestoneRow extends StatelessWidget {
  final MilestoneResponse milestone;
  const _MilestoneRow({required this.milestone});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = theme.status;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          milestone.title,
          style: theme.textTheme.bodyLarge?.copyWith(color: status.onGoodContainer, fontWeight: FontWeight.w600),
        ),
        const SizedBox(height: 2),
        Text(
          milestone.description,
          style: theme.textTheme.bodySmall?.copyWith(color: status.onGoodContainer),
        ),
      ],
    );
  }
}
