// Fallback compiled wherever dart:html isn't available — the native VM
// used by `flutter test`, and any future non-web target. Throws rather than
// silently no-oping so a real tap on a download button on an unsupported
// platform fails loudly instead of doing nothing. See web_download.dart for
// the conditional-import facade.
void downloadTextAsFile(String content, String filename) {
  throw UnsupportedError(
    'downloadTextAsFile is only implemented for Flutter web. '
    'This app currently targets web only (see docs/product/release-plan.md Phase 4).',
  );
}
