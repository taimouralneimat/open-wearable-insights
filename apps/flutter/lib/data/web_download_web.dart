// Real implementation, compiled only when dart:html is available (Flutter
// web). See web_download.dart for the conditional-import facade.
import 'dart:html' as html;

void downloadTextAsFile(String content, String filename) {
  final bytes = html.Blob([content], 'application/json');
  final url = html.Url.createObjectUrlFromBlob(bytes);
  html.AnchorElement(href: url)
    ..setAttribute('download', filename)
    ..click();
  html.Url.revokeObjectUrl(url);
}
