import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'app/app.dart';
import 'data/token_store.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // Must complete before the router builds its first route, so
  // TokenStore.paired already reflects reality (no flash of the wrong screen).
  await TokenStore.init();
  runApp(
    const ProviderScope(
      child: OpenWearableInsightsApp(),
    ),
  );
}