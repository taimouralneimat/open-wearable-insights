import 'package:flutter/material.dart';
import 'package:open_wearable_insights/app/theme.dart';

/// Shows a surface's confidence and limitations, per the project's core
/// principle that every score/insight exposes its own uncertainty rather
/// than presenting placeholder or low-confidence data as if it were real.
///
/// Renders nothing when confidence is a normal, non-placeholder level and
/// there are no limitations to disclose, so this is safe to include
/// unconditionally on every data page.
class ConfidenceBanner extends StatelessWidget {
  final String confidence;
  final List<String> limitations;

  const ConfidenceBanner({
    super.key,
    required this.confidence,
    required this.limitations,
  });

  bool get _isPlaceholder => confidence == 'none';

  @override
  Widget build(BuildContext context) {
    if (limitations.isEmpty) return const SizedBox.shrink();

    final status = Theme.of(context).status;
    final fg = _isPlaceholder ? status.onPoorContainer : status.onFairContainer;
    final bg = _isPlaceholder ? status.poorContainer : status.fairContainer;
    final textTheme = Theme.of(context).textTheme;

    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.lg),
      child: Container(
        padding: const EdgeInsets.all(AppSpacing.md),
        decoration: BoxDecoration(
          color: bg,
          borderRadius: BorderRadius.circular(AppRadius.md),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  _isPlaceholder ? Icons.science_outlined : Icons.info_outline,
                  size: 18,
                  color: fg,
                ),
                const SizedBox(width: AppSpacing.sm),
                Text(
                  _isPlaceholder ? 'Placeholder data' : 'Confidence: $confidence',
                  style: textTheme.labelLarge?.copyWith(color: fg),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.xs),
            ...limitations.map((l) => Padding(
                  padding: const EdgeInsets.only(top: 2),
                  child: Text(l, style: textTheme.bodySmall?.copyWith(color: fg)),
                )),
          ],
        ),
      ),
    );
  }
}
