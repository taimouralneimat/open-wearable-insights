import 'package:flutter_test/flutter_test.dart';

import 'package:open_wearable_insights/app/app.dart';

void main() {
  testWidgets('App shell renders the dashboard route', (WidgetTester tester) async {
    await tester.pumpWidget(const OpenWearableInsightsApp());
    await tester.pumpAndSettle();

    expect(find.byType(OpenWearableInsightsApp), findsOneWidget);
  });
}
