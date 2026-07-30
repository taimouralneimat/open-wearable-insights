// Web-only: triggers a browser file download from in-memory text. The app
// currently targets Flutter web only (see docs/product/release-plan.md
// Phase 4 — native mobile hasn't started), so a dart:html-only
// implementation is acceptable for now; this file needs a conditional
// dart:io/dart:html split if/when mobile support lands.
import 'dart:html' as html;

void downloadTextAsFile(String content, String filename) {
  final bytes = html.Blob([content], 'application/json');
  final url = html.Url.createObjectUrlFromBlob(bytes);
  html.AnchorElement(href: url)
    ..setAttribute('download', filename)
    ..click();
  html.Url.revokeObjectUrl(url);
}
