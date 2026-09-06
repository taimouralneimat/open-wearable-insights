import 'package:flutter/material.dart';
import 'package:open_wearable_insights/app/theme.dart';

/// Small, honest indicator that a piece of coach text was genuinely
/// rewritten by the local LLM (see LlmInsightService on the backend) rather
/// than the deterministic template engine — shown only when the backend's
/// own `llmUsed` field is true, never assumed from whether the LLM is
/// merely enabled. Renders nothing otherwise, so this is safe to include
/// unconditionally next to any coach text field.
class LlmBadge extends StatelessWidget {
  final bool llmUsed;

  const LlmBadge({super.key, required this.llmUsed});

  @override
  Widget build(BuildContext context) {
    if (!llmUsed) return const SizedBox.shrink();

    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Tooltip(
      message: 'Rewritten by your local AI from the same computed facts shown above — never a new number or claim.',
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.sm, vertical: 2),
        decoration: BoxDecoration(
          color: scheme.primaryContainer,
          borderRadius: BorderRadius.circular(AppRadius.pill),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.auto_awesome, size: 12, color: scheme.onPrimaryContainer),
            const SizedBox(width: 4),
            Text(
              'Personalized locally',
              style: theme.textTheme.labelSmall?.copyWith(color: scheme.onPrimaryContainer),
            ),
          ],
        ),
      ),
    );
  }
}
