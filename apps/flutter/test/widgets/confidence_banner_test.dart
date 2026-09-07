import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:open_wearable_insights/app/theme.dart';
import 'package:open_wearable_insights/widgets/confidence_banner.dart';

/// Regression coverage for the app's core "every score exposes its own
/// uncertainty" principle (parity-matrix row 12) — this banner is the one
/// shared surface that principle depends on, used unconditionally across
/// every data page, so a silent regression here would quietly undermine
/// that guarantee everywhere at once.
void main() {
  Widget wrap(Widget child) => MaterialApp(
        theme: AppTheme.light(),
        home: Scaffold(body: child),
      );

  testWidgets('renders nothing when there are no limitations to disclose', (tester) async {
    await tester.pumpWidget(wrap(const ConfidenceBanner(confidence: 'high', limitations: [])));

    expect(find.byType(SizedBox), findsOneWidget);
    expect(find.textContaining('Confidence'), findsNothing);
  });

  testWidgets('shows confidence level and every limitation when present', (tester) async {
    await tester.pumpWidget(wrap(const ConfidenceBanner(
      confidence: 'medium',
      limitations: ['First limitation.', 'Second limitation.'],
    )));

    expect(find.text('Confidence: medium'), findsOneWidget);
    expect(find.text('First limitation.'), findsOneWidget);
    expect(find.text('Second limitation.'), findsOneWidget);
    expect(find.byIcon(Icons.info_outline), findsOneWidget);
    expect(find.text('Placeholder data'), findsNothing);
  });

  testWidgets('labels confidence "none" as placeholder data, not a numeric confidence level', (tester) async {
    await tester.pumpWidget(wrap(const ConfidenceBanner(
      confidence: 'none',
      limitations: ['Not enough history yet.'],
    )));

    expect(find.text('Placeholder data'), findsOneWidget);
    expect(find.text('Confidence: none'), findsNothing);
    expect(find.byIcon(Icons.science_outlined), findsOneWidget);
  });
}
