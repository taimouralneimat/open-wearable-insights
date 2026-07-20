import 'package:dio/dio.dart';

/// Typed API client for Open Wearable Insights.
///
/// All network calls go through this layer; no direct Dio calls in widgets.
class ApiClient {
  final Dio _dio;

  ApiClient()
      : _dio = Dio(BaseOptions(
          baseUrl: 'http://127.0.0.1:8080',
          connectTimeout: const Duration(seconds: 5),
          receiveTimeout: const Duration(seconds: 10),
        ));

  /// Get the latest readiness score.
  Future<ReadinessResponse> getLatestReadiness() async {
    final response = await _dio.get('/api/v1/readiness/latest');
    return ReadinessResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// Get the daily coach insight.
  Future<InsightResponse> getInsight() async {
    final response = await _dio.get('/api/v1/coach/insight');
    return InsightResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// Get LLM status.
  Future<LlmStatus> getLlmStatus() async {
    final response = await _dio.get('/api/v1/coach/status');
    return LlmStatus.fromJson(response.data as Map<String, dynamic>);
  }
}

/// Readiness score response from the API.
class ReadinessResponse {
  final int score;
  final String algorithmVersion;
  final bool provisional;
  final String baselinePeriod;
  final String confidence;
  final String dataQuality;
  final List<FactorResponse> factors;
  final String missingDataTreatment;
  final String explanation;
  final String limitations;

  ReadinessResponse({
    required this.score,
    required this.algorithmVersion,
    required this.provisional,
    required this.baselinePeriod,
    required this.confidence,
    required this.dataQuality,
    required this.factors,
    required this.missingDataTreatment,
    required this.explanation,
    required this.limitations,
  });

  factory ReadinessResponse.fromJson(Map<String, dynamic> json) {
    return ReadinessResponse(
      score: json['score'] as int,
      algorithmVersion: json['algorithmVersion'] as String,
      provisional: json['provisional'] as bool,
      baselinePeriod: json['baselinePeriod'] as String,
      confidence: json['confidence'] as String,
      dataQuality: json['dataQuality'] as String,
      factors: (json['factors'] as List)
          .map((e) => FactorResponse.fromJson(e as Map<String, dynamic>))
          .toList(),
      missingDataTreatment: json['missingDataTreatment'] as String,
      explanation: json['explanation'] as String,
      limitations: json['limitations'] as String,
    );
  }
}

class FactorResponse {
  final String name;
  final double value;
  final String unit;
  final String direction;
  final double contribution;
  final String source;

  FactorResponse({
    required this.name,
    required this.value,
    required this.unit,
    required this.direction,
    required this.contribution,
    required this.source,
  });

  factory FactorResponse.fromJson(Map<String, dynamic> json) {
    return FactorResponse(
      name: json['name'] as String,
      value: (json['value'] as num).toDouble(),
      unit: json['unit'] as String,
      direction: json['direction'] as String,
      contribution: (json['contribution'] as num).toDouble(),
      source: json['source'] as String,
    );
  }
}

class InsightResponse {
  final String headline;
  final String summary;
  final List<FactorSummaryResponse> supportingFactors;
  final List<String> recommendedActions;
  final String confidence;
  final List<String> cautions;
  final List<String> dataLimitations;
  final bool fallbackUsed;

  InsightResponse({
    required this.headline,
    required this.summary,
    required this.supportingFactors,
    required this.recommendedActions,
    required this.confidence,
    required this.cautions,
    required this.dataLimitations,
    required this.fallbackUsed,
  });

  factory InsightResponse.fromJson(Map<String, dynamic> json) {
    return InsightResponse(
      headline: json['headline'] as String,
      summary: json['summary'] as String,
      supportingFactors: (json['supportingFactors'] as List)
          .map((e) => FactorSummaryResponse.fromJson(e as Map<String, dynamic>))
          .toList(),
      recommendedActions: (json['recommendedActions'] as List).cast<String>(),
      confidence: json['confidence'] as String,
      cautions: (json['cautions'] as List).cast<String>(),
      dataLimitations: (json['dataLimitations'] as List).cast<String>(),
      fallbackUsed: json['fallbackUsed'] as bool,
    );
  }
}

class FactorSummaryResponse {
  final String name;
  final String value;
  final String direction;

  FactorSummaryResponse({
    required this.name,
    required this.value,
    required this.direction,
  });

  factory FactorSummaryResponse.fromJson(Map<String, dynamic> json) {
    return FactorSummaryResponse(
      name: json['name'] as String,
      value: json['value'] as String,
      direction: json['direction'] as String,
    );
  }
}

class LlmStatus {
  final bool enabled;
  final String mode;

  LlmStatus({required this.enabled, required this.mode});

  factory LlmStatus.fromJson(Map<String, dynamic> json) {
    return LlmStatus(
      enabled: json['enabled'] as bool,
      mode: json['mode'] as String,
    );
  }
}