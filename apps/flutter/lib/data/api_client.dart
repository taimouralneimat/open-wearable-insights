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

  /// Ask the coach why the readiness score is what it is.
  Future<WhyAnswerResponse> getWhyAnswer() async {
    final response = await _dio.get('/api/v1/coach/why');
    return WhyAnswerResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// Scan the local import directory for importable files.
  Future<ImportScanResult> scanImportDirectory() async {
    final response = await _dio.get('/api/v1/ingestion/scan');
    return ImportScanResult.fromJson(response.data as Map<String, dynamic>);
  }

  /// Dry-run validation of import files.
  Future<DryRunSummary> dryRunValidation() async {
    final response = await _dio.get('/api/v1/ingestion/dry-run');
    return DryRunSummary.fromJson(response.data as Map<String, dynamic>);
  }

  /// Import all supported files from the import directory.
  Future<ImportProgress> importAll() async {
    final response = await _dio.post('/api/v1/ingestion/import');
    return ImportProgress.fromJson(response.data as Map<String, dynamic>);
  }

  /// Undo an import batch.
  Future<UndoResult> undoBatch(int batchId) async {
    final response = await _dio.post('/api/v1/ingestion/undo/$batchId');
    return UndoResult.fromJson(response.data as Map<String, dynamic>);
  }

  /// List all import batches.
  Future<List<ImportBatchResponse>> listBatches() async {
    final response = await _dio.get('/api/v1/ingestion/batches');
    return (response.data as List)
        .map((e) => ImportBatchResponse.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Get sleep summary.
  Future<SleepSummary> getSleepSummary() async {
    final response = await _dio.get('/api/v1/sleep/summary');
    return SleepSummary.fromJson(response.data as Map<String, dynamic>);
  }

  /// Get sleep trends.
  Future<List<SleepTrendPoint>> getSleepTrends() async {
    final response = await _dio.get('/api/v1/sleep/trends');
    return (response.data as List)
        .map((e) => SleepTrendPoint.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Get activity summary.
  Future<ActivitySummary> getActivitySummary() async {
    final response = await _dio.get('/api/v1/activities/summary');
    return ActivitySummary.fromJson(response.data as Map<String, dynamic>);
  }

  /// Get activity trends.
  Future<List<ActivityTrendPoint>> getActivityTrends() async {
    final response = await _dio.get('/api/v1/activities/trends');
    return (response.data as List)
        .map((e) => ActivityTrendPoint.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Get score diff vs prior day.
  Future<ScoreDiffResponse> getScoreDiff() async {
    final response = await _dio.get("/api/v1/readiness/diff");
    return ScoreDiffResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// Get data-quality summary.
  Future<DataQualitySummary> getDataQualitySummary() async {
    final response = await _dio.get('/api/v1/data-quality/summary');
    return DataQualitySummary.fromJson(response.data as Map<String, dynamic>);
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

/// A grounded answer to "why is my readiness what it is", citing the
/// actual computed factors — never generic advice.
class WhyAnswerResponse {
  final String answer;
  final List<CitedMetricResponse> citedMetrics;
  final String confidence;
  final List<String> limitations;

  WhyAnswerResponse({
    required this.answer,
    required this.citedMetrics,
    required this.confidence,
    required this.limitations,
  });

  factory WhyAnswerResponse.fromJson(Map<String, dynamic> json) {
    return WhyAnswerResponse(
      answer: json['answer'] as String,
      citedMetrics: (json['citedMetrics'] as List)
          .map((e) => CitedMetricResponse.fromJson(e as Map<String, dynamic>))
          .toList(),
      confidence: json['confidence'] as String,
      limitations: (json['limitations'] as List).cast<String>(),
    );
  }
}

class CitedMetricResponse {
  final String name;
  final String value;
  final String contribution;
  final String direction;

  CitedMetricResponse({
    required this.name,
    required this.value,
    required this.contribution,
    required this.direction,
  });

  factory CitedMetricResponse.fromJson(Map<String, dynamic> json) {
    return CitedMetricResponse(
      name: json['name'] as String,
      value: json['value'] as String,
      contribution: json['contribution'] as String,
      direction: json['direction'] as String,
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

/// Result of scanning the local import directory.
class ImportScanResult {
  final List<ScannedFileResponse> files;
  final String directory;
  final bool exists;
  final String? errorMessage;

  ImportScanResult({
    required this.files,
    required this.directory,
    required this.exists,
    this.errorMessage,
  });

  factory ImportScanResult.fromJson(Map<String, dynamic> json) {
    return ImportScanResult(
      files: (json['files'] as List)
          .map((e) => ScannedFileResponse.fromJson(e as Map<String, dynamic>))
          .toList(),
      directory: json['directory'] as String,
      exists: json['exists'] as bool,
      errorMessage: json['errorMessage'] as String?,
    );
  }
}

class ScannedFileResponse {
  final String filename;
  final int sizeBytes;
  final String lastModified;
  final String detectedFormat;
  final bool supported;

  ScannedFileResponse({
    required this.filename,
    required this.sizeBytes,
    required this.lastModified,
    required this.detectedFormat,
    required this.supported,
  });

  factory ScannedFileResponse.fromJson(Map<String, dynamic> json) {
    return ScannedFileResponse(
      filename: json['filename'] as String,
      sizeBytes: json['sizeBytes'] as int,
      lastModified: json['lastModified'] as String,
      detectedFormat: json['detectedFormat'] as String,
      supported: json['supported'] as bool,
    );
  }
}


/// Summary of a dry-run validation pass.
class DryRunSummary {
  final List<ValidationResultResponse> results;
  final String directory;
  final bool exists;
  final String? errorMessage;
  final int supportedFiles;
  final int duplicates;
  final int totalRecords;
  final int unsupportedFiles;

  DryRunSummary({
    required this.results,
    required this.directory,
    required this.exists,
    this.errorMessage,
    required this.supportedFiles,
    required this.duplicates,
    required this.totalRecords,
    required this.unsupportedFiles,
  });

  factory DryRunSummary.fromJson(Map<String, dynamic> json) {
    return DryRunSummary(
      results: (json['results'] as List)
          .map((e) => ValidationResultResponse.fromJson(e as Map<String, dynamic>))
          .toList(),
      directory: json['directory'] as String,
      exists: json['exists'] as bool,
      errorMessage: json['errorMessage'] as String?,
      supportedFiles: json['supportedFiles'] as int,
      duplicates: json['duplicates'] as int,
      totalRecords: json['totalRecords'] as int,
      unsupportedFiles: json['unsupportedFiles'] as int,
    );
  }
}

class ValidationResultResponse {
  final String filename;
  final String detectedFormat;
  final bool supported;
  final String? contentHash;
  final int recordCount;
  final bool duplicate;
  final List<String> errors;
  final List<String> warnings;
  final List<String> unsupportedRecords;

  ValidationResultResponse({
    required this.filename,
    required this.detectedFormat,
    required this.supported,
    this.contentHash,
    required this.recordCount,
    required this.duplicate,
    required this.errors,
    required this.warnings,
    required this.unsupportedRecords,
  });

  factory ValidationResultResponse.fromJson(Map<String, dynamic> json) {
    return ValidationResultResponse(
      filename: json['filename'] as String,
      detectedFormat: json['detectedFormat'] as String,
      supported: json['supported'] as bool,
      contentHash: json['contentHash'] as String?,
      recordCount: json['recordCount'] as int,
      duplicate: json['duplicate'] as bool,
      errors: (json['errors'] as List).cast<String>(),
      warnings: (json['warnings'] as List).cast<String>(),
      unsupportedRecords: (json['unsupportedRecords'] as List).cast<String>(),
    );
  }
}


/// Import progress report.
class ImportProgress {
  final List<FileImportResult> files;
  final String directory;
  final bool exists;
  final String? errorMessage;
  final int imported;
  final int skipped;
  final int failed;
  final int totalRecords;

  ImportProgress({
    required this.files,
    required this.directory,
    required this.exists,
    this.errorMessage,
    required this.imported,
    required this.skipped,
    required this.failed,
    required this.totalRecords,
  });

  factory ImportProgress.fromJson(Map<String, dynamic> json) {
    return ImportProgress(
      files: (json['files'] as List)
          .map((e) => FileImportResult.fromJson(e as Map<String, dynamic>))
          .toList(),
      directory: json['directory'] as String,
      exists: json['exists'] as bool,
      errorMessage: json['errorMessage'] as String?,
      imported: json['imported'] as int,
      skipped: json['skipped'] as int,
      failed: json['failed'] as int,
      totalRecords: json['totalRecords'] as int,
    );
  }
}

class FileImportResult {
  final String filename;
  final String status;
  final String? message;
  final int recordCount;
  final String? contentHash;

  FileImportResult({
    required this.filename,
    required this.status,
    this.message,
    required this.recordCount,
    this.contentHash,
  });

  factory FileImportResult.fromJson(Map<String, dynamic> json) {
    return FileImportResult(
      filename: json['filename'] as String,
      status: json['status'] as String,
      message: json['message'] as String?,
      recordCount: json['recordCount'] as int,
      contentHash: json['contentHash'] as String?,
    );
  }
}

class UndoResult {
  final bool success;
  final String message;
  final int? batchId;
  final String? fileName;

  UndoResult({
    required this.success,
    required this.message,
    this.batchId,
    this.fileName,
  });

  factory UndoResult.fromJson(Map<String, dynamic> json) {
    return UndoResult(
      success: json['success'] as bool,
      message: json['message'] as String,
      batchId: json['batchId'] as int?,
      fileName: json['fileName'] as String?,
    );
  }
}

class ImportBatchResponse {
  final int id;
  final int accountId;
  final String source;
  final String contentHash;
  final String status;
  final String importedAt;
  final String? undoAt;
  final String? fileName;
  final int recordCount;

  ImportBatchResponse({
    required this.id,
    required this.accountId,
    required this.source,
    required this.contentHash,
    required this.status,
    required this.importedAt,
    this.undoAt,
    this.fileName,
    required this.recordCount,
  });

  factory ImportBatchResponse.fromJson(Map<String, dynamic> json) {
    return ImportBatchResponse(
      id: json['id'] as int,
      accountId: json['accountId'] as int,
      source: json['source'] as String,
      contentHash: json['contentHash'] as String,
      status: json['status'] as String,
      importedAt: json['importedAt'] as String,
      undoAt: json['undoAt'] as String?,
      fileName: json['fileName'] as String?,
      recordCount: json['recordCount'] as int,
    );
  }
}


/// Sleep summary response.
class SleepSummary {
  final double totalHours;
  final double deepHours;
  final double remHours;
  final double lightHours;
  final double awakeHours;
  final int sleepScore;
  final List<SleepStagePoint> stages;

  SleepSummary({
    required this.totalHours,
    required this.deepHours,
    required this.remHours,
    required this.lightHours,
    required this.awakeHours,
    required this.sleepScore,
    required this.stages,
  });

  factory SleepSummary.fromJson(Map<String, dynamic> json) {
    return SleepSummary(
      totalHours: (json['totalHours'] as num).toDouble(),
      deepHours: (json['deepHours'] as num).toDouble(),
      remHours: (json['remHours'] as num).toDouble(),
      lightHours: (json['lightHours'] as num).toDouble(),
      awakeHours: (json['awakeHours'] as num).toDouble(),
      sleepScore: json['sleepScore'] as int,
      stages: (json['stages'] as List)
          .map((e) => SleepStagePoint.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

class SleepStagePoint {
  final String time;
  final String stage;

  SleepStagePoint({required this.time, required this.stage});

  factory SleepStagePoint.fromJson(Map<String, dynamic> json) {
    return SleepStagePoint(
      time: json['time'] as String,
      stage: json['stage'] as String,
    );
  }
}

class SleepTrendPoint {
  final String date;
  final double totalHours;
  final double deepHours;
  final double remHours;
  final double lightHours;
  final double awakeHours;
  final int sleepScore;

  SleepTrendPoint({
    required this.date,
    required this.totalHours,
    required this.deepHours,
    required this.remHours,
    required this.lightHours,
    required this.awakeHours,
    required this.sleepScore,
  });

  factory SleepTrendPoint.fromJson(Map<String, dynamic> json) {
    return SleepTrendPoint(
      date: json['date'] as String,
      totalHours: (json['totalHours'] as num).toDouble(),
      deepHours: (json['deepHours'] as num).toDouble(),
      remHours: (json['remHours'] as num).toDouble(),
      lightHours: (json['lightHours'] as num).toDouble(),
      awakeHours: (json['awakeHours'] as num).toDouble(),
      sleepScore: json['sleepScore'] as int,
    );
  }
}

/// Activity summary response.
class ActivitySummary {
  final int steps;
  final int calories;
  final int activeMinutes;
  final int activeZoneMinutes;
  final String timestamp;

  ActivitySummary({
    required this.steps,
    required this.calories,
    required this.activeMinutes,
    required this.activeZoneMinutes,
    required this.timestamp,
  });

  factory ActivitySummary.fromJson(Map<String, dynamic> json) {
    return ActivitySummary(
      steps: json['steps'] as int,
      calories: json['calories'] as int,
      activeMinutes: json['activeMinutes'] as int,
      activeZoneMinutes: json['activeZoneMinutes'] as int,
      timestamp: json['timestamp'] as String,
    );
  }
}

class ActivityTrendPoint {
  final String date;
  final int steps;
  final int calories;
  final int activeMinutes;

  ActivityTrendPoint({
    required this.date,
    required this.steps,
    required this.calories,
    required this.activeMinutes,
  });

  factory ActivityTrendPoint.fromJson(Map<String, dynamic> json) {
    return ActivityTrendPoint(
      date: json['date'] as String,
      steps: json['steps'] as int,
      calories: json['calories'] as int,
      activeMinutes: json['activeMinutes'] as int,
    );
  }
}


/// Data-quality summary response.
class DataQualitySummary {
  final double completeness;
  final String freshness;
  final int daysOfData;
  final int totalSources;
  final List<MetricQuality> metrics;
  final List<String> issues;
  final String algorithmVersion;

  DataQualitySummary({
    required this.completeness,
    required this.freshness,
    required this.daysOfData,
    required this.totalSources,
    required this.metrics,
    required this.issues,
    required this.algorithmVersion,
  });

  factory DataQualitySummary.fromJson(Map<String, dynamic> json) {
    return DataQualitySummary(
      completeness: (json['completeness'] as num).toDouble(),
      freshness: json['freshness'] as String,
      daysOfData: json['daysOfData'] as int,
      totalSources: json['totalSources'] as int,
      metrics: (json['metrics'] as List)
          .map((e) => MetricQuality.fromJson(e as Map<String, dynamic>))
          .toList(),
      issues: (json['issues'] as List).cast<String>(),
      algorithmVersion: json['algorithmVersion'] as String,
    );
  }
}

class MetricQuality {
  final String metric;
  final double coverage;
  final String quality;
  final String frequency;

  MetricQuality({
    required this.metric,
    required this.coverage,
    required this.quality,
    required this.frequency,
  });

  factory MetricQuality.fromJson(Map<String, dynamic> json) {
    return MetricQuality(
      metric: json['metric'] as String,
      coverage: (json['coverage'] as num).toDouble(),
      quality: json['quality'] as String,
      frequency: json['frequency'] as String,
    );
  }
}


/// Score diff response from the API.
class ScoreDiffResponse {
  final int todayScore;
  final int priorScore;
  final int scoreDelta;
  final List<FactorDiffResponse> factorDiffs;
  final String summary;
  final String? biggestPositive;
  final String? biggestNegative;
  final String comparedAgainst;

  ScoreDiffResponse({
    required this.todayScore,
    required this.priorScore,
    required this.scoreDelta,
    required this.factorDiffs,
    required this.summary,
    this.biggestPositive,
    this.biggestNegative,
    required this.comparedAgainst,
  });

  bool get hasPriorData => comparedAgainst == 'yesterday';

  factory ScoreDiffResponse.fromJson(Map<String, dynamic> json) {
    return ScoreDiffResponse(
      todayScore: json['todayScore'] as int,
      priorScore: json['priorScore'] as int,
      scoreDelta: json['scoreDelta'] as int,
      factorDiffs: (json['factorDiffs'] as List)
          .map((e) => FactorDiffResponse.fromJson(e as Map<String, dynamic>))
          .toList(),
      summary: json['summary'] as String,
      biggestPositive: json['biggestPositive'] as String?,
      biggestNegative: json['biggestNegative'] as String?,
      comparedAgainst: json['comparedAgainst'] as String? ?? 'no_prior_data',
    );
  }
}

class FactorDiffResponse {
  final String name;
  final double todayValue;
  final double priorValue;
  final double valueDelta;
  final double todayContribution;
  final double priorContribution;
  final double contributionDelta;
  final String direction;

  FactorDiffResponse({
    required this.name,
    required this.todayValue,
    required this.priorValue,
    required this.valueDelta,
    required this.todayContribution,
    required this.priorContribution,
    required this.contributionDelta,
    required this.direction,
  });

  factory FactorDiffResponse.fromJson(Map<String, dynamic> json) {
    return FactorDiffResponse(
      name: json['name'] as String,
      todayValue: (json['todayValue'] as num).toDouble(),
      priorValue: (json['priorValue'] as num).toDouble(),
      valueDelta: (json['valueDelta'] as num).toDouble(),
      todayContribution: (json['todayContribution'] as num).toDouble(),
      priorContribution: (json['priorContribution'] as num).toDouble(),
      contributionDelta: (json['contributionDelta'] as num).toDouble(),
      direction: json['direction'] as String,
    );
  }
}
