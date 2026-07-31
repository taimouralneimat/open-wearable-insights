import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Persists the local API token (ADR-0008) via platform secure storage and
/// exposes pairing status as a [Listenable] so go_router can gate every
/// route on it (see app/app.dart's GoRouter redirect + refreshListenable).
///
/// Replaces the compile-time-constant token that shipped as an emergency
/// stopgap when per-request auth was first enforced.
class TokenStore {
  TokenStore._();

  static const _key = 'owi_local_api_token';
  static const _storage = FlutterSecureStorage();

  /// True once the initial read from storage has completed. Call [init]
  /// and await it before runApp so this is already true — and [paired]
  /// already correct — by the time the router builds its first route.
  static bool initialized = false;

  static final ValueNotifier<bool> paired = ValueNotifier(false);

  static Future<void> init() async {
    final token = await _storage.read(key: _key);
    paired.value = token != null && token.isNotEmpty;
    initialized = true;
  }

  static Future<String?> read() => _storage.read(key: _key);

  static Future<void> write(String token) async {
    await _storage.write(key: _key, value: token);
    paired.value = true;
  }

  /// Called when a request comes back 401 (token wrong or rotated
  /// server-side) as well as from a manual "disconnect" action. Routes the
  /// app straight back to pairing via the [paired] listenable.
  static Future<void> clear() async {
    await _storage.delete(key: _key);
    paired.value = false;
  }
}
