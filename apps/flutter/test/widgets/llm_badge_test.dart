import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:open_wearable_insights/app/theme.dart';
import 'package:open_wearable_insights/widgets/llm_badge.dart';

/// Regression coverage for the one visible signal a user has that a piece
/// of coach text was genuinely LLM-rephrased (parity-matrix row 7) — a
/// silent regression here (e.g. the badge always showing, or never
/// showing) would be a real honesty bug, not just a cosmetic one.
void main() {
  Widget wrap(Widget child) => MaterialApp(
        theme: AppTheme.light(),
        home: Scaffold(body: child),
      );

  testWidgets('renders nothing when llmUsed is false', (tester) async {
    await tester.pumpWidget(wrap(const LlmBadge(llmUsed: false)));

    expect(find.byType(SizedBox), findsOneWidget);
    expect(find.text('Personalized locally'), findsNothing);
    expect(find.byIcon(Icons.auto_awesome), findsNothing);
  });

  testWidgets('renders the pill with icon and label when llmUsed is true', (tester) async {
    await tester.pumpWidget(wrap(const LlmBadge(llmUsed: true)));

    expect(find.text('Personalized locally'), findsOneWidget);
    expect(find.byIcon(Icons.auto_awesome), findsOneWidget);
    expect(find.byType(Tooltip), findsOneWidget);
  });

  testWidgets('tooltip explains this rewrites text, never invents facts', (tester) async {
    await tester.pumpWidget(wrap(const LlmBadge(llmUsed: true)));

    final tooltip = tester.widget<Tooltip>(find.byType(Tooltip));
    expect(tooltip.message, contains('never a new number or claim'));
  });
}
