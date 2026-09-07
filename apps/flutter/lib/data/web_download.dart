// Conditional-import facade: the real dart:html implementation is only
// compiled where dart:html exists (Flutter web); every other target
// (including the native VM `flutter test` runs on, and any future mobile
// build) gets the stub. This app currently targets Flutter web only (see
// docs/product/release-plan.md Phase 4), so the stub only needs to exist so
// this file compiles everywhere, not to do anything useful yet.
export 'web_download_stub.dart' if (dart.library.html) 'web_download_web.dart';
