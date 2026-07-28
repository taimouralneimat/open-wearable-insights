import 'package:flutter/material.dart';

/// Shows a surface's confidence and limitations, per the project's core
/// principle that every score/insight exposes its own uncertainty rather
/// than presenting placeholder or low-confidence data as if it were real.
///
/// Renders nothing when confidence is a normal, non-placeholder level and
/// there are no limitations to disclose, so this is safe to include
/// unconditionally on every data page.
class ConfidenceBanner extends StatelessWidget {
  final String confidence;
  final List<String> limitations;

  const ConfidenceBanner({
    super.key,
    required this.confidence,
    required this.limitations,
  });

  bool get _isPlaceholder => confidence == 'none';

  @override
  Widget build(BuildContext context) {
    if (limitations.isEmpty) return const SizedBox.shrink();

    final color = _isPlaceholder ? Colors.red : Colors.orange;

    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Card(
        color: color.shade50,
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(
                    _isPlaceholder ? Icons.science_outlined : Icons.info_outline,
                    size: 18,
                    color: color.shade800,
                  ),
                  const SizedBox(width: 8),
                  Text(
                    _isPlaceholder
                        ? 'Placeholder data'
                        : 'Confidence: $confidence',
                    style: TextStyle(fontWeight: FontWeight.w600, color: color.shade800),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              ...limitations.map((l) => Padding(
                    padding: const EdgeInsets.only(top: 2),
                    child: Text(l, style: TextStyle(fontSize: 12, color: color.shade900)),
                  )),
            ],
          ),
        ),
      ),
    );
  }
}
