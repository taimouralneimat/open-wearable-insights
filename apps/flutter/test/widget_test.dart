import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:open_wearable_insights/app/app.dart';

void main() {
  testWidgets('App shell renders the dashboard route', (WidgetTester tester) async {
    await tester.pumpWidget(
      const ProviderScope(
        child: OpenWearableInsightsApp(),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(OpenWearableInsightsApp), findsOneWidget);
  });
}
