import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:open_wearable_insights/app/theme.dart';
import 'package:open_wearable_insights/widgets/state_views.dart';

/// Regression coverage for the three shared loading/error/empty
/// placeholders every data page in this app renders through — a
/// regression here (e.g. a dropped retry callback) would be silent and
/// widespread rather than confined to one screen.
void main() {
  Widget wrap(Widget child) => MaterialApp(
        theme: AppTheme.light(),
        home: Scaffold(body: child),
      );

  group('LoadingView', () {
    testWidgets('shows a spinner and the given label', (tester) async {
      await tester.pumpWidget(wrap(const LoadingView(label: 'Loading readiness…')));

      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.text('Loading readiness…'), findsOneWidget);
    });
  });

  group('ErrorView', () {
    testWidgets('shows the default title when none is given', (tester) async {
      await tester.pumpWidget(wrap(ErrorView(message: 'Network error', onRetry: () {})));

      expect(find.text("Couldn't load this page"), findsOneWidget);
      expect(find.text('Network error'), findsOneWidget);
    });

    testWidgets('shows a custom title when given', (tester) async {
      await tester.pumpWidget(wrap(ErrorView(
        title: "Couldn't load the dashboard",
        message: 'Network error',
        onRetry: () {},
      )));

      expect(find.text("Couldn't load the dashboard"), findsOneWidget);
    });

    testWidgets('invokes onRetry when the Retry button is tapped', (tester) async {
      var retried = false;
      await tester.pumpWidget(wrap(ErrorView(
        message: 'Network error',
        onRetry: () => retried = true,
      )));

      await tester.tap(find.widgetWithText(FilledButton, 'Retry'));
      await tester.pump();

      expect(retried, isTrue);
    });
  });

  group('EmptyView', () {
    testWidgets('shows icon and title, no message/action when not given', (tester) async {
      await tester.pumpWidget(wrap(const EmptyView(
        icon: Icons.monitor_heart_outlined,
        title: 'No data yet',
      )));

      expect(find.byIcon(Icons.monitor_heart_outlined), findsOneWidget);
      expect(find.text('No data yet'), findsOneWidget);
    });

    testWidgets('shows message and action widget when given', (tester) async {
      await tester.pumpWidget(wrap(EmptyView(
        icon: Icons.monitor_heart_outlined,
        title: 'No data yet',
        message: 'Import wearable data to see your readiness score.',
        action: FilledButton(onPressed: () {}, child: const Text('Import data')),
      )));

      expect(find.text('Import wearable data to see your readiness score.'), findsOneWidget);
      expect(find.widgetWithText(FilledButton, 'Import data'), findsOneWidget);
    });
  });
}
