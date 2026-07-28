import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:open_wearable_insights/features/readiness/dashboard_page.dart';
import 'package:open_wearable_insights/features/import/import_page.dart';
import 'package:open_wearable_insights/features/sleep/sleep_page.dart';
import 'package:open_wearable_insights/features/activities/activities_page.dart';
import 'package:open_wearable_insights/features/settings/data_quality_page.dart';

/// Open Wearable Insights — app shell with routing.
///
/// Original visual language. Does not imitate any vendor's design.
class OpenWearableInsightsApp extends StatelessWidget {
  const OpenWearableInsightsApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'Open Wearable Insights',
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF2E8B57)),
      ),
      routerConfig: _router,
    );
  }

  static final GoRouter _router = GoRouter(
    initialLocation: '/',
    routes: [
      GoRoute(
        path: '/',
        builder: (context, state) => const DashboardPage(),
      ),
      GoRoute(
        path: '/import',
        builder: (context, state) => const ImportPage(),
      ),
      GoRoute(
        path: '/sleep',
        builder: (context, state) => const SleepPage(),
      ),
      GoRoute(
        path: '/activities',
        builder: (context, state) => const ActivitiesPage(),
      ),
      GoRoute(
        path: '/data-quality',
        builder: (context, state) => const DataQualityPage(),
      ),
    ],
  );
}
