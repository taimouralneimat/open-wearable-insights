import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:open_wearable_insights/app/theme.dart';
import 'package:open_wearable_insights/features/readiness/dashboard_page.dart';
import 'package:open_wearable_insights/features/import/import_page.dart';
import 'package:open_wearable_insights/features/sleep/sleep_page.dart';
import 'package:open_wearable_insights/features/activities/activities_page.dart';
import 'package:open_wearable_insights/features/activities/activity_session_detail_page.dart';
import 'package:open_wearable_insights/features/activities/training_load_page.dart';
import 'package:open_wearable_insights/features/settings/data_quality_page.dart';
import 'package:open_wearable_insights/features/journal/journal_page.dart';
import 'package:open_wearable_insights/features/profile/profile_page.dart';

/// Open Wearable Insights — app shell with routing.
///
/// Original visual language. Does not imitate any vendor's design.
class OpenWearableInsightsApp extends StatelessWidget {
  const OpenWearableInsightsApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'Open Wearable Insights',
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      themeMode: ThemeMode.system,
      routerConfig: _router,
    );
  }

  static final GoRouter _router = GoRouter(
    initialLocation: '/',
    routes: [
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) => AppShell(navigationShell: navigationShell),
        branches: [
          StatefulShellBranch(
            routes: [GoRoute(path: '/', builder: (context, state) => const DashboardPage())],
          ),
          StatefulShellBranch(
            routes: [GoRoute(path: '/sleep', builder: (context, state) => const SleepPage())],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/activities',
                builder: (context, state) => const ActivitiesPage(),
                routes: [
                  GoRoute(
                    path: 'sessions/:id',
                    builder: (context, state) => ActivitySessionDetailPage(
                      id: int.parse(state.pathParameters['id']!),
                    ),
                  ),
                  GoRoute(
                    path: 'training-load',
                    builder: (context, state) => const TrainingLoadPage(),
                  ),
                ],
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [GoRoute(path: '/journal', builder: (context, state) => const JournalPage())],
          ),
        ],
      ),
      GoRoute(
        path: '/import',
        builder: (context, state) => const ImportPage(),
      ),
      GoRoute(
        path: '/data-quality',
        builder: (context, state) => const DataQualityPage(),
      ),
      GoRoute(
        path: '/profile',
        builder: (context, state) => const ProfilePage(),
      ),
    ],
  );
}

/// Persistent bottom navigation around the four daily-use surfaces.
///
/// Import and data-quality are infrequent, "settings-like" actions —
/// they're reached from the overflow menu on each tab's app bar instead of
/// competing for a bottom-nav slot (kept to <=5 destinations per platform
/// guidance).
class AppShell extends StatelessWidget {
  final StatefulNavigationShell navigationShell;
  const AppShell({super.key, required this.navigationShell});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: navigationShell,
      bottomNavigationBar: NavigationBar(
        selectedIndex: navigationShell.currentIndex,
        onDestinationSelected: (index) => navigationShell.goBranch(
          index,
          initialLocation: index == navigationShell.currentIndex,
        ),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.monitor_heart_outlined),
            selectedIcon: Icon(Icons.monitor_heart),
            label: 'Today',
          ),
          NavigationDestination(
            icon: Icon(Icons.bedtime_outlined),
            selectedIcon: Icon(Icons.bedtime),
            label: 'Sleep',
          ),
          NavigationDestination(
            icon: Icon(Icons.directions_run_outlined),
            selectedIcon: Icon(Icons.directions_run),
            label: 'Activity',
          ),
          NavigationDestination(
            icon: Icon(Icons.edit_note_outlined),
            selectedIcon: Icon(Icons.edit_note),
            label: 'Journal',
          ),
        ],
      ),
    );
  }
}

/// Overflow menu used on each tab's app bar for the secondary "more" actions
/// (import, data quality) that don't belong in the primary bottom nav.
class MoreMenuButton extends StatelessWidget {
  const MoreMenuButton({super.key});

  @override
  Widget build(BuildContext context) {
    return PopupMenuButton<String>(
      tooltip: 'More',
      icon: const Icon(Icons.more_vert),
      onSelected: (value) => context.push('/$value'),
      itemBuilder: (context) => const [
        PopupMenuItem(
          value: 'profile',
          child: ListTile(
            leading: Icon(Icons.person_outline),
            title: Text('Profile'),
            contentPadding: EdgeInsets.zero,
          ),
        ),
        PopupMenuItem(
          value: 'import',
          child: ListTile(
            leading: Icon(Icons.upload_file_outlined),
            title: Text('Import data'),
            contentPadding: EdgeInsets.zero,
          ),
        ),
        PopupMenuItem(
          value: 'data-quality',
          child: ListTile(
            leading: Icon(Icons.fact_check_outlined),
            title: Text('Data quality'),
            contentPadding: EdgeInsets.zero,
          ),
        ),
      ],
    );
  }
}
