import 'package:dio/dio.dart';
import 'token_store.dart';

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
        )) {
    // Real local API token auth (ADR-0008), replacing the compile-time
    // constant this used to hardcode. The token is read fresh per request
    // rather than cached on the client so a mid-session pairing (or
    // re-pairing after a 401 below) takes effect immediately.
    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        final token = await TokenStore.read();
        if (token != null && token.isNotEmpty) {
          options.headers['X-Local-Api-Token'] = token;
        }
        handler.next(options);
      },
      onError: (error, handler) {
        if (error.response?.statusCode == 401) {
          // Token is wrong or was rotated server-side. Clear it — this
          // flips TokenStore.paired, which go_router's redirect (wired via
          // refreshListenable in app.dart) picks up and routes back to the
          // pairing screen automatically.
          TokenStore.clear();
        }
        handler.next(error);
      },
    ));
  }

  /// Get the local account's profile (display name, primary goal).
  Future<ProfileResponse> getProfile() async {
    final response = await _dio.get('/api/v1/profile');
    return ProfileResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// Update the local account's profile.
  Future<ProfileResponse> updateProfile({String? displayName, String? primaryGoal}) async {
    final response = await _dio.put('/api/v1/profile', data: {
      'displayName': displayName,
      'primaryGoal': primaryGoal,
    });
    return ProfileResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// Get suggested primary-goal options for the profile editor.
  Future<List<String>> getGoalOptions() async {
    final response = await _dio.get('/api/v1/profile/goal-options');
    return (response.data as List).cast<String>();
  }

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

  /// Get today's habit cue — one specific journal behavior suggested from
  /// today's worst real readiness factor.
  Future<HabitCueResponse> getHabitCue() async {
    final response = await _dio.get('/api/v1/coach/habit-cue');
    return HabitCueResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// Get current/longest streaks per logged behavior.
  Future<List<HabitStreakResponse>> getHabitStreaks() async {
    final response = await _dio.get('/api/v1/journal/streaks');
    return (response.data as List)
        .map((e) => HabitStreakResponse.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Get identity-based habit "votes" toward the profile's primary goal.
  Future<IdentityVotesResponse> getIdentityVotes() async {
    final response = await _dio.get('/api/v1/journal/identity-votes');
    return IdentityVotesResponse.fromJson(response.data as Map<String, dynamic>);
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

  /// Get accumulated sleep debt/surplus and personal sleep need.
  Future<SleepDebtResponse> getSleepDebt() async {
    final response = await _dio.get('/api/v1/sleep/debt');
    return SleepDebtResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// Get tonight's bedtime recommendation.
  Future<SleepPlanResponse> getSleepPlan() async {
    final response = await _dio.get('/api/v1/sleep/plan');
    return SleepPlanResponse.fromJson(response.data as Map<String, dynamic>);
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

  /// List recent discrete workout sessions (runs, rides, ...) — distinct
  /// from the day-level step summary above. See ADR-0006.
  Future<List<ActivitySessionResponse>> getActivitySessions() async {
    final response = await _dio.get('/api/v1/activities/sessions');
    return (response.data as List)
        .map((e) => ActivitySessionResponse.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Get full detail for one activity session, including HR-zone breakdown.
  Future<ActivitySessionResponse> getActivitySession(int id) async {
    final response = await _dio.get('/api/v1/activities/sessions/$id');
    return ActivitySessionResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// Get the current training-load (ACWR) summary.
  Future<TrainingLoadSummaryResponse> getTrainingLoadSummary() async {
    final response = await _dio.get('/api/v1/trainingload/summary');
    return TrainingLoadSummaryResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// Get daily training-load trend (up to 28 days, active days only).
  Future<List<TrainingLoadTrendPointResponse>> getTrainingLoadTrends() async {
    final response = await _dio.get('/api/v1/trainingload/trends');
    return (response.data as List)
        .map((e) => TrainingLoadTrendPointResponse.fromJson(e as Map<String, dynamic>))
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

  /// Get the curated journal behavior taxonomy.
  Future<List<BehaviorCategory>> getJournalBehaviors() async {
    final response = await _dio.get('/api/v1/journal/behaviors');
    return (response.data as List)
        .map((e) => BehaviorCategory.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Log a journal entry.
  Future<JournalEntryResponse> addJournalEntry({
    String? category,
    required String behavior,
    String? value,
    String? note,
  }) async {
    final response = await _dio.post('/api/v1/journal/entries', data: {
      'category': category,
      'behavior': behavior,
      'value': value,
      'note': note,
    });
    return JournalEntryResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// List journal entries, most recent first.
  Future<List<JournalEntryResponse>> getJournalEntries() async {
    final response = await _dio.get('/api/v1/journal/entries');
    return (response.data as List)
        .map((e) => JournalEntryResponse.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Get exploratory behavior correlations. Only includes behaviors that
  /// meet a minimum sample size — never fabricates confidence.
  Future<List<BehaviorCorrelationResponse>> getCorrelations() async {
    final response = await _dio.get('/api/v1/journal/correlations');
    return (response.data as List)
        .map((e) => BehaviorCorrelationResponse.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Fetch the complete local data export as raw JSON text, plus the
  /// server-suggested filename from Content-Disposition. Returns the raw
  /// body rather than parsing it — this is a pass-through to a file
  /// download, not data the app itself needs to read.
  Future<(String body, String filename)> fetchFullExport() async {
    final response = await _dio.get<String>(
      '/api/v1/export/full',
      options: Options(responseType: ResponseType.plain),
    );
    final disposition = response.headers.value('content-disposition') ?? '';
    final match = RegExp(r'filename="([^"]+)"').firstMatch(disposition);
    final filename = match?.group(1) ?? 'open-wearable-insights-export.json';
    return (response.data ?? '', filename);
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

/// One specific habit suggestion triggered by today's worst real readiness
/// factor. [present] false means there's honestly nothing to suggest —
/// never fabricated.
class HabitCueResponse {
  final bool present;
  final String? triggerFactor;
  final double triggerContribution;
  final String? suggestedCategory;
  final String? suggestedBehavior;
  final String reasoning;
  final String confidence;

  HabitCueResponse({
    required this.present,
    this.triggerFactor,
    required this.triggerContribution,
    this.suggestedCategory,
    this.suggestedBehavior,
    required this.reasoning,
    required this.confidence,
  });

  factory HabitCueResponse.fromJson(Map<String, dynamic> json) {
    return HabitCueResponse(
      present: json['present'] as bool,
      triggerFactor: json['triggerFactor'] as String?,
      triggerContribution: (json['triggerContribution'] as num).toDouble(),
      suggestedCategory: json['suggestedCategory'] as String?,
      suggestedBehavior: json['suggestedBehavior'] as String?,
      reasoning: json['reasoning'] as String,
      confidence: json['confidence'] as String,
    );
  }
}

/// A logged behavior's consecutive-day streak.
class HabitStreakResponse {
  final String category;
  final String behavior;
  final int currentStreak;
  final int longestStreak;
  final String lastLoggedDate;

  HabitStreakResponse({
    required this.category,
    required this.behavior,
    required this.currentStreak,
    required this.longestStreak,
    required this.lastLoggedDate,
  });

  factory HabitStreakResponse.fromJson(Map<String, dynamic> json) {
    return HabitStreakResponse(
      category: json['category'] as String,
      behavior: json['behavior'] as String,
      currentStreak: json['currentStreak'] as int,
      longestStreak: json['longestStreak'] as int,
      lastLoggedDate: json['lastLoggedDate'] as String,
    );
  }
}

/// Identity-based habit "votes" (Atomic Habits framing) — entries logged in
/// categories relevant to the profile's stated primary goal. [goal] is null
/// when no goal is set, never guessed.
class IdentityVotesResponse {
  final String? goal;
  final int votes;
  final int totalEntries;
  final int windowDays;
  final List<String> relevantCategories;

  IdentityVotesResponse({
    this.goal,
    required this.votes,
    required this.totalEntries,
    required this.windowDays,
    required this.relevantCategories,
  });

  factory IdentityVotesResponse.fromJson(Map<String, dynamic> json) {
    return IdentityVotesResponse(
      goal: json['goal'] as String?,
      votes: json['votes'] as int,
      totalEntries: json['totalEntries'] as int,
      windowDays: json['windowDays'] as int,
      relevantCategories: (json['relevantCategories'] as List).cast<String>(),
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
  final double? sleepNeedHours;
  final List<SleepStagePoint> stages;
  final String confidence;
  final List<String> limitations;

  SleepSummary({
    required this.totalHours,
    required this.deepHours,
    required this.remHours,
    required this.lightHours,
    required this.awakeHours,
    required this.sleepScore,
    this.sleepNeedHours,
    required this.stages,
    required this.confidence,
    required this.limitations,
  });

  factory SleepSummary.fromJson(Map<String, dynamic> json) {
    return SleepSummary(
      totalHours: (json['totalHours'] as num).toDouble(),
      deepHours: (json['deepHours'] as num).toDouble(),
      remHours: (json['remHours'] as num).toDouble(),
      lightHours: (json['lightHours'] as num).toDouble(),
      awakeHours: (json['awakeHours'] as num).toDouble(),
      sleepScore: json['sleepScore'] as int,
      sleepNeedHours: (json['sleepNeedHours'] as num?)?.toDouble(),
      stages: (json['stages'] as List)
          .map((e) => SleepStagePoint.fromJson(e as Map<String, dynamic>))
          .toList(),
      confidence: json['confidence'] as String? ?? 'none',
      limitations: (json['limitations'] as List?)?.cast<String>() ?? const [],
    );
  }
}

/// Accumulated sleep debt/surplus over a recent window — see
/// SleepInsightService.computeSleepDebt on the backend. Both fields null
/// when there isn't enough history yet for a personal need estimate.
class SleepDebtResponse {
  final double? neededHoursPerNight;
  final double? accumulatedHours;
  final int nightsConsidered;
  final int windowDays;
  final String confidence;
  final List<String> limitations;

  SleepDebtResponse({
    this.neededHoursPerNight,
    this.accumulatedHours,
    required this.nightsConsidered,
    required this.windowDays,
    required this.confidence,
    required this.limitations,
  });

  factory SleepDebtResponse.fromJson(Map<String, dynamic> json) {
    return SleepDebtResponse(
      neededHoursPerNight: (json['neededHoursPerNight'] as num?)?.toDouble(),
      accumulatedHours: (json['accumulatedHours'] as num?)?.toDouble(),
      nightsConsidered: json['nightsConsidered'] as int,
      windowDays: json['windowDays'] as int,
      confidence: json['confidence'] as String,
      limitations: (json['limitations'] as List?)?.cast<String>() ?? const [],
    );
  }
}

/// Tonight's bedtime recommendation — see SleepInsightService.computeSleepPlan
/// on the backend. Both time fields null when there isn't enough history.
class SleepPlanResponse {
  final String? recommendedBedtime;
  final String? targetWakeTime;
  final double? targetSleepHours;
  final double debtRepaymentHours;
  final String reasoning;
  final String confidence;
  final List<String> limitations;

  SleepPlanResponse({
    this.recommendedBedtime,
    this.targetWakeTime,
    this.targetSleepHours,
    required this.debtRepaymentHours,
    required this.reasoning,
    required this.confidence,
    required this.limitations,
  });

  factory SleepPlanResponse.fromJson(Map<String, dynamic> json) {
    return SleepPlanResponse(
      recommendedBedtime: json['recommendedBedtime'] as String?,
      targetWakeTime: json['targetWakeTime'] as String?,
      targetSleepHours: (json['targetSleepHours'] as num?)?.toDouble(),
      debtRepaymentHours: (json['debtRepaymentHours'] as num).toDouble(),
      reasoning: json['reasoning'] as String,
      confidence: json['confidence'] as String,
      limitations: (json['limitations'] as List?)?.cast<String>() ?? const [],
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

/// Activity summary response. Only [steps] is computed from real data —
/// calories/activeMinutes/activeZoneMinutes are null when not tracked
/// (see ActivityInsightService on the backend for why).
class ActivitySummary {
  final int steps;
  final int? calories;
  final int? activeMinutes;
  final int? activeZoneMinutes;
  final String timestamp;
  final String confidence;
  final List<String> limitations;

  ActivitySummary({
    required this.steps,
    this.calories,
    this.activeMinutes,
    this.activeZoneMinutes,
    required this.timestamp,
    required this.confidence,
    required this.limitations,
  });

  factory ActivitySummary.fromJson(Map<String, dynamic> json) {
    return ActivitySummary(
      steps: json['steps'] as int,
      calories: json['calories'] as int?,
      activeMinutes: json['activeMinutes'] as int?,
      activeZoneMinutes: json['activeZoneMinutes'] as int?,
      timestamp: json['timestamp'] as String,
      confidence: json['confidence'] as String? ?? 'none',
      limitations: (json['limitations'] as List?)?.cast<String>() ?? const [],
    );
  }
}

/// A discrete workout/activity session (e.g. a run) — distinct from
/// [ActivitySummary], which is a day-level step rollup. Parsed from
/// connector data on import; see ADR-0006 and ActivitySessionRepository
/// on the backend.
class ActivitySessionResponse {
  final int id;
  final String sport;
  final String startTime;
  final String endTime;
  final double durationSeconds;
  final double? distanceMeters;
  final double? avgSpeedMps;
  final double? maxSpeedMps;
  final double? avgPaceSecPerKm;
  final int? avgHeartRate;
  final int? maxHeartRate;
  final int? calories;
  final List<double> hrZoneSeconds;

  ActivitySessionResponse({
    required this.id,
    required this.sport,
    required this.startTime,
    required this.endTime,
    required this.durationSeconds,
    this.distanceMeters,
    this.avgSpeedMps,
    this.maxSpeedMps,
    this.avgPaceSecPerKm,
    this.avgHeartRate,
    this.maxHeartRate,
    this.calories,
    required this.hrZoneSeconds,
  });

  factory ActivitySessionResponse.fromJson(Map<String, dynamic> json) {
    return ActivitySessionResponse(
      id: json['id'] as int,
      sport: json['sport'] as String,
      startTime: json['startTime'] as String,
      endTime: json['endTime'] as String,
      durationSeconds: (json['durationSeconds'] as num).toDouble(),
      distanceMeters: (json['distanceMeters'] as num?)?.toDouble(),
      avgSpeedMps: (json['avgSpeedMps'] as num?)?.toDouble(),
      maxSpeedMps: (json['maxSpeedMps'] as num?)?.toDouble(),
      avgPaceSecPerKm: (json['avgPaceSecPerKm'] as num?)?.toDouble(),
      avgHeartRate: json['avgHeartRate'] as int?,
      maxHeartRate: json['maxHeartRate'] as int?,
      calories: json['calories'] as int?,
      hrZoneSeconds: (json['hrZoneSeconds'] as List?)?.map((e) => (e as num).toDouble()).toList() ?? const [],
    );
  }
}

/// Current training-load (ACWR) snapshot. acuteLoad/chronicLoad/acwr are
/// null when there isn't enough recent activity — never a fabricated zero.
class TrainingLoadSummaryResponse {
  final double? acuteLoad;
  final double? chronicLoad;
  final double? acwr;
  final String loadStatus;
  final String algorithmVersion;
  final String confidence;
  final List<String> limitations;

  TrainingLoadSummaryResponse({
    this.acuteLoad,
    this.chronicLoad,
    this.acwr,
    required this.loadStatus,
    required this.algorithmVersion,
    required this.confidence,
    required this.limitations,
  });

  factory TrainingLoadSummaryResponse.fromJson(Map<String, dynamic> json) {
    return TrainingLoadSummaryResponse(
      acuteLoad: (json['acuteLoad'] as num?)?.toDouble(),
      chronicLoad: (json['chronicLoad'] as num?)?.toDouble(),
      acwr: (json['acwr'] as num?)?.toDouble(),
      loadStatus: json['loadStatus'] as String,
      algorithmVersion: json['algorithmVersion'] as String,
      confidence: json['confidence'] as String,
      limitations: (json['limitations'] as List).cast<String>(),
    );
  }
}

/// A single day's total training load. Only days with an activity appear —
/// rest days are omitted, not zero-filled.
class TrainingLoadTrendPointResponse {
  final String date;
  final double load;

  TrainingLoadTrendPointResponse({required this.date, required this.load});

  factory TrainingLoadTrendPointResponse.fromJson(Map<String, dynamic> json) {
    return TrainingLoadTrendPointResponse(
      date: json['date'] as String,
      load: (json['load'] as num).toDouble(),
    );
  }
}

/// A day's step count within a trend view. Calories/active minutes aren't
/// tracked in the current data model — see ActivitySummary.
class ActivityTrendPoint {
  final String date;
  final int steps;

  ActivityTrendPoint({
    required this.date,
    required this.steps,
  });

  factory ActivityTrendPoint.fromJson(Map<String, dynamic> json) {
    return ActivityTrendPoint(
      date: json['date'] as String,
      steps: json['steps'] as int,
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
  final String confidence;
  final List<String> limitations;

  DataQualitySummary({
    required this.completeness,
    required this.freshness,
    required this.daysOfData,
    required this.totalSources,
    required this.metrics,
    required this.issues,
    required this.algorithmVersion,
    required this.confidence,
    required this.limitations,
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
      confidence: json['confidence'] as String? ?? 'none',
      limitations: (json['limitations'] as List?)?.cast<String>() ?? const [],
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
  final String confidence;

  ScoreDiffResponse({
    required this.todayScore,
    required this.priorScore,
    required this.scoreDelta,
    required this.factorDiffs,
    required this.summary,
    this.biggestPositive,
    this.biggestNegative,
    required this.comparedAgainst,
    required this.confidence,
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
      confidence: json['confidence'] as String? ?? 'low',
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

/// A category of suggested journal behaviors.
class BehaviorCategory {
  final String name;
  final List<String> behaviors;

  BehaviorCategory({required this.name, required this.behaviors});

  factory BehaviorCategory.fromJson(Map<String, dynamic> json) {
    return BehaviorCategory(
      name: json['name'] as String,
      behaviors: (json['behaviors'] as List).cast<String>(),
    );
  }
}

/// A logged journal entry — self-reported, always treated as untrusted input.
class JournalEntryResponse {
  final int id;
  final String time;
  final String category;
  final String behavior;
  final String? value;
  final String? note;
  final bool treatedAsUntrusted;

  JournalEntryResponse({
    required this.id,
    required this.time,
    required this.category,
    required this.behavior,
    this.value,
    this.note,
    required this.treatedAsUntrusted,
  });

  factory JournalEntryResponse.fromJson(Map<String, dynamic> json) {
    return JournalEntryResponse(
      id: json['id'] as int,
      time: json['time'] as String,
      category: json['category'] as String,
      behavior: json['behavior'] as String,
      value: json['value'] as String?,
      note: json['note'] as String?,
      treatedAsUntrusted: json['treatedAsUntrusted'] as bool,
    );
  }
}

/// An exploratory correlation between a logged behavior and readiness —
/// never causation. See CorrelationService on the backend.
class BehaviorCorrelationResponse {
  final String category;
  final String behavior;
  final int loggedDayCount;
  final int notLoggedDayCount;
  final double avgReadinessWhenLogged;
  final double avgReadinessWhenNotLogged;
  final double difference;
  final String confidence;
  final List<String> limitations;

  BehaviorCorrelationResponse({
    required this.category,
    required this.behavior,
    required this.loggedDayCount,
    required this.notLoggedDayCount,
    required this.avgReadinessWhenLogged,
    required this.avgReadinessWhenNotLogged,
    required this.difference,
    required this.confidence,
    required this.limitations,
  });

  factory BehaviorCorrelationResponse.fromJson(Map<String, dynamic> json) {
    return BehaviorCorrelationResponse(
      category: json['category'] as String,
      behavior: json['behavior'] as String,
      loggedDayCount: json['loggedDayCount'] as int,
      notLoggedDayCount: json['notLoggedDayCount'] as int,
      avgReadinessWhenLogged: (json['avgReadinessWhenLogged'] as num).toDouble(),
      avgReadinessWhenNotLogged: (json['avgReadinessWhenNotLogged'] as num).toDouble(),
      difference: (json['difference'] as num).toDouble(),
      confidence: json['confidence'] as String,
      limitations: (json['limitations'] as List).cast<String>(),
    );
  }
}

/// The local account's profile — display name and primary goal. Both
/// nullable: a fresh account has neither set yet.
class ProfileResponse {
  final int accountId;
  final String email;
  final String? displayName;
  final String? primaryGoal;

  ProfileResponse({
    required this.accountId,
    required this.email,
    this.displayName,
    this.primaryGoal,
  });

  factory ProfileResponse.fromJson(Map<String, dynamic> json) {
    return ProfileResponse(
      accountId: json['accountId'] as int,
      email: json['email'] as String,
      displayName: json['displayName'] as String?,
      primaryGoal: json['primaryGoal'] as String?,
    );
  }
}
