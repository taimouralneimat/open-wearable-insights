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

  /// Discover real wellness data already staged locally by Garmin Express,
  /// before it uploads to Garmin Connect's cloud. Read-only.
  Future<List<GarminExpressDeviceResponse>> getGarminExpressDevices() async {
    final response = await _dio.get('/api/v1/ingestion/garmin-express/devices');
    return (response.data as List)
        .map((e) => GarminExpressDeviceResponse.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Copies (never moves) a device's real files from Garmin Express's local
  /// folder into the import directory. Returns how many were newly copied.
  Future<int> stageGarminExpressDevice(String deviceId) async {
    final response = await _dio.post('/api/v1/ingestion/garmin-express/devices/$deviceId/stage');
    return (response.data as Map<String, dynamic>)['filesCopied'] as int;
  }

  /// Get the current Garmin Connect connection status.
  Future<GarminConnectStatusResponse> getGarminConnectStatus() async {
    final response = await _dio.get('/api/v1/garmin-connect/status');
    return GarminConnectStatusResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// Log in to Garmin Connect with the account's real credentials. The
  /// password is sent once, directly to this endpoint, and never stored —
  /// only the resulting session tokens are.
  Future<GarminConnectConnectResponse> garminConnectLogin(String email, String password) async {
    final response = await _dio.post('/api/v1/garmin-connect/connect', data: {
      'email': email,
      'password': password,
    });
    return GarminConnectConnectResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// Submit the MFA code Garmin sent, to complete an in-progress login.
  Future<GarminConnectConnectResponse> garminConnectSubmitMfa(String code) async {
    final response = await _dio.post('/api/v1/garmin-connect/mfa', data: {'code': code});
    return GarminConnectConnectResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// Disconnect Garmin Connect and discard stored tokens.
  Future<void> garminConnectDisconnect() async {
    await _dio.delete('/api/v1/garmin-connect');
  }

  /// Fetch real historical sleep/HRV/stress/RHR/steps data for a date range.
  ///
  /// Backed by a deliberately throttled sync (see GarminConnectSyncService —
  /// ~300ms between days plus real network calls to Garmin, to avoid
  /// tripping Garmin's abuse detection), so this one call can legitimately
  /// take minutes for a large range. Uses a much longer receive timeout than
  /// the client's 10s default, which exists for normal fast local-API calls
  /// and would otherwise report a spurious failure while the backend keeps
  /// working to completion regardless.
  Future<GarminConnectSyncResultResponse> garminConnectSync(DateTime startDate, DateTime endDate) async {
    String iso(DateTime d) => d.toIso8601String().split('T').first;
    final response = await _dio.post(
      '/api/v1/garmin-connect/sync',
      data: {
        'startDate': iso(startDate),
        'endDate': iso(endDate),
      },
      options: Options(receiveTimeout: const Duration(minutes: 15)),
    );
    return GarminConnectSyncResultResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// Get the most recent Body Battery and Garmin Training Readiness values —
  /// Garmin-exclusive metrics this app doesn't blend into its own readiness
  /// score, shown separately so the two stay distinct.
  Future<GarminEnrichmentResponse> getGarminEnrichmentToday() async {
    final response = await _dio.get('/api/v1/garmin-connect/enrichment/today');
    return GarminEnrichmentResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// Get the last 14 days of Body Battery high/low values.
  Future<List<BodyBatteryTrendPointResponse>> getBodyBatteryTrend() async {
    final response = await _dio.get('/api/v1/garmin-connect/enrichment/body-battery/trend');
    return (response.data as List)
        .map((e) => BodyBatteryTrendPointResponse.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Get sleep summary.
  Future<SleepSummary> getSleepSummary() async {
    final response = await _dio.get('/api/v1/sleep/summary');
    return SleepSummary.fromJson(response.data as Map<String, dynamic>);
  }

  /// Get sleep trends, plus the account's real personal sleep-duration
  /// baseline for a reference line on the chart (parity-matrix row 8).
  /// [days] defaults to 7 server-side if omitted.
  Future<SleepTrendResponse> getSleepTrends({int? days}) async {
    final response = await _dio.get('/api/v1/sleep/trends', queryParameters: days != null ? {'days': days} : null);
    return SleepTrendResponse.fromJson(response.data as Map<String, dynamic>);
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

  /// Get sleep consistency (bed/wake time regularity) over recent nights.
  Future<SleepConsistencyResponse> getSleepConsistency() async {
    final response = await _dio.get('/api/v1/sleep/consistency');
    return SleepConsistencyResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// Get activity summary.
  Future<ActivitySummary> getActivitySummary() async {
    final response = await _dio.get('/api/v1/activities/summary');
    return ActivitySummary.fromJson(response.data as Map<String, dynamic>);
  }

  /// Get activity trends, plus the account's real personal steps baseline
  /// for a reference line on the chart (parity-matrix row 8). [days]
  /// defaults to 7 server-side if omitted.
  Future<ActivityTrendResponse> getActivityTrends({int? days}) async {
    final response = await _dio.get('/api/v1/activities/trends', queryParameters: days != null ? {'days': days} : null);
    return ActivityTrendResponse.fromJson(response.data as Map<String, dynamic>);
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

  /// Get the current VO2max estimate.
  Future<Vo2MaxEstimateResponse> getVo2MaxEstimate() async {
    final response = await _dio.get('/api/v1/vo2max');
    return Vo2MaxEstimateResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// Get the monthly VO2max trend (up to 12 months, months with insufficient data omitted).
  Future<List<Vo2MaxTrendPointResponse>> getVo2MaxTrend() async {
    final response = await _dio.get('/api/v1/vo2max/trend');
    return (response.data as List)
        .map((e) => Vo2MaxTrendPointResponse.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Get the composite healthspan/wellness score (30-day and 6-month windows).
  Future<HealthspanSummaryResponse> getHealthspanSummary() async {
    final response = await _dio.get('/api/v1/healthspan');
    return HealthspanSummaryResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// Get the monthly performance report (strain/sleep/recovery breakdown).
  /// [month] is ISO yyyy-MM (e.g. "2026-06"); omit for the most recently
  /// complete calendar month.
  Future<MonthlyReportResponse> getMonthlyReport({String? month}) async {
    final response = await _dio.get(
      '/api/v1/monthly-report',
      queryParameters: month != null ? {'month': month} : null,
    );
    return MonthlyReportResponse.fromJson(response.data as Map<String, dynamic>);
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

  /// Get exploratory correlations between behaviors and Garmin-exclusive
  /// signals (Body Battery, Garmin's own Training Readiness score). Empty
  /// until a Garmin Connect sync has provided that data.
  Future<List<GarminSignalCorrelationResponse>> getGarminSignalCorrelations() async {
    final response = await _dio.get('/api/v1/journal/correlations/garmin-signals');
    return (response.data as List)
        .map((e) => GarminSignalCorrelationResponse.fromJson(e as Map<String, dynamic>))
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

  /// Permanently deletes every personal-data table's rows for this account —
  /// the GDPR-erasure counterpart to [fetchFullExport]. Irreversible. The
  /// backend independently requires confirm=DELETE as its own safety check,
  /// on top of whatever confirmation this app's own UI requires before ever
  /// calling this.
  Future<Map<String, int>> deleteAllData() async {
    final response = await _dio.delete(
      '/api/v1/export/all',
      queryParameters: {'confirm': 'DELETE'},
    );
    return (response.data as Map<String, dynamic>).map((k, v) => MapEntry(k, v as int));
  }

  /// Rotates the local API token — invalidates the current one and returns
  /// a new one. The caller must store the new token immediately; this
  /// request's own token stops working right after this call succeeds.
  Future<String> regenerateToken() async {
    final response = await _dio.post('/api/v1/auth/regenerate-token');
    return (response.data as Map<String, dynamic>)['token'] as String;
  }

  /// List imported blood biomarker (lab bloodwork) readings, most recent
  /// first. Real CSV-imported rows only — empty list if nothing imported yet.
  Future<List<BiomarkerReadingResponse>> getBiomarkerReadings({String? name}) async {
    final response = await _dio.get('/api/v1/biomarkers/readings', queryParameters: name != null ? {'name': name} : null);
    return (response.data as List)
        .map((e) => BiomarkerReadingResponse.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Distinct biomarker names this account has at least one real reading
  /// for — used to populate a trend picker without guessing at names.
  Future<List<String>> getBiomarkerNames() async {
    final response = await _dio.get('/api/v1/biomarkers/names');
    return (response.data as List).cast<String>();
  }

  /// Every imported reading for one biomarker name, chronological.
  Future<List<BiomarkerReadingResponse>> getBiomarkerTrend(String biomarkerName) async {
    final response = await _dio.get('/api/v1/biomarkers/trend/${Uri.encodeComponent(biomarkerName)}');
    return (response.data as List)
        .map((e) => BiomarkerReadingResponse.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Static reference catalog (name/category/plain-language description) —
  /// no numeric ranges, see BiomarkerReferenceCatalog on the backend.
  Future<List<BiomarkerReferenceEntryResponse>> getBiomarkerReference() async {
    final response = await _dio.get('/api/v1/biomarkers/reference');
    return (response.data as List)
        .map((e) => BiomarkerReferenceEntryResponse.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Log a manually-tracked strength workout. [startedAt] defaults to now
  /// server-side if omitted. [durationMinutes] is optional — when omitted,
  /// the backend estimates one from set count for trend purposes rather
  /// than measuring it (see StrengthTrainingService).
  Future<StrengthWorkoutResponse> logStrengthWorkout({
    DateTime? startedAt,
    int? durationMinutes,
    String? note,
    required List<StrengthExerciseInput> exercises,
  }) async {
    final response = await _dio.post('/api/v1/strength/workouts', data: {
      'startedAt': startedAt?.toUtc().toIso8601String(),
      'durationMinutes': durationMinutes,
      'note': note,
      'exercises': exercises.map((e) => e.toJson()).toList(),
    });
    return StrengthWorkoutResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// List recent manually-logged strength workouts, most recent first.
  Future<List<StrengthWorkoutResponse>> getStrengthWorkouts() async {
    final response = await _dio.get('/api/v1/strength/workouts');
    return (response.data as List)
        .map((e) => StrengthWorkoutResponse.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Get the real "Strength Activity Time" trend. [window] is 'weekly'
  /// (last 7 days, daily buckets), 'monthly' (last 30 days, weekly
  /// buckets), or 'sixmonth' (last 180 days, monthly buckets).
  Future<StrengthActivityTrendResponse> getStrengthTrends({String window = 'weekly'}) async {
    final response = await _dio.get('/api/v1/strength/trends', queryParameters: {'window': window});
    return StrengthActivityTrendResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// Get the optional weekly strength-minutes goal — null if unset.
  Future<int?> getStrengthGoalMinutes() async {
    final response = await _dio.get('/api/v1/strength/goal');
    return (response.data as Map<String, dynamic>)['weeklyGoalMinutes'] as int?;
  }

  /// Set (or clear, with null) the weekly strength-minutes goal.
  Future<int?> setStrengthGoalMinutes(int? minutes) async {
    final response = await _dio.put('/api/v1/strength/goal', data: {'weeklyGoalMinutes': minutes});
    return (response.data as Map<String, dynamic>)['weeklyGoalMinutes'] as int?;
  }

  /// Generate a deterministic, template-based custom workout (parity-matrix
  /// row 26). No LLM is called — see the response's disclaimer/source
  /// fields. [equipment] and [limitations] are the enum names the backend
  /// expects (e.g. 'DUMBBELLS', 'KNEE') — bodyweight is always implicitly
  /// available and never needs to be included in [equipment].
  Future<GeneratedWorkoutResponse> generateWorkout({
    required String goal,
    required List<String> equipment,
    required List<String> limitations,
    required int durationMinutes,
  }) async {
    final response = await _dio.post('/api/v1/workout-gen/generate', data: {
      'goal': goal,
      'equipment': equipment,
      'limitations': limitations,
      'durationMinutes': durationMinutes,
    });
    return GeneratedWorkoutResponse.fromJson(response.data as Map<String, dynamic>);
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

/// A Garmin device with real wellness data staged locally by Garmin
/// Express, before it uploads to Garmin Connect's cloud. See
/// GarminExpressLocator on the backend — Garmin Connect's web export
/// doesn't offer daily wellness data (sleep/steps/stress/HRV) at all, so
/// this is the real path to it without official Garmin API access.
class GarminExpressDeviceResponse {
  final String deviceId;
  final List<GarminExpressCategoryResponse> categories;
  final int totalFiles;

  GarminExpressDeviceResponse({
    required this.deviceId,
    required this.categories,
    required this.totalFiles,
  });

  factory GarminExpressDeviceResponse.fromJson(Map<String, dynamic> json) {
    return GarminExpressDeviceResponse(
      deviceId: json['deviceId'] as String,
      categories: (json['categories'] as List)
          .map((e) => GarminExpressCategoryResponse.fromJson(e as Map<String, dynamic>))
          .toList(),
      totalFiles: json['totalFiles'] as int,
    );
  }
}

class GarminExpressCategoryResponse {
  final String name;
  final int fileCount;

  GarminExpressCategoryResponse({required this.name, required this.fileCount});

  factory GarminExpressCategoryResponse.fromJson(Map<String, dynamic> json) {
    return GarminExpressCategoryResponse(
      name: json['name'] as String,
      fileCount: json['fileCount'] as int,
    );
  }
}


/// Sleep summary response.
class SleepSummary {
  /// Time in bed — deep + rem + light + awake. What sleep debt/plan and the
  /// readiness score's baseline use; see [asleepHours] for true sleep time.
  final double totalHours;
  /// Deep + rem + light only, excluding awake-in-bed time. Display only.
  final double asleepHours;
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
    required this.asleepHours,
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
      asleepHours: (json['asleepHours'] as num?)?.toDouble() ?? 0,
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

/// How regular bed/wake times have been recently — see
/// SleepInsightService.computeSleepConsistency on the backend. Distinct
/// from sleep duration/debt: this is about *when*, not *how much*.
class SleepConsistencyResponse {
  final int? consistencyScore;
  final String? avgBedtime;
  final String? avgWakeTime;
  final double? bedtimeVarianceMinutes;
  final double? wakeTimeVarianceMinutes;
  final int nightsConsidered;
  final String confidence;
  final List<String> limitations;

  SleepConsistencyResponse({
    this.consistencyScore,
    this.avgBedtime,
    this.avgWakeTime,
    this.bedtimeVarianceMinutes,
    this.wakeTimeVarianceMinutes,
    required this.nightsConsidered,
    required this.confidence,
    required this.limitations,
  });

  factory SleepConsistencyResponse.fromJson(Map<String, dynamic> json) {
    return SleepConsistencyResponse(
      consistencyScore: json['consistencyScore'] as int?,
      avgBedtime: json['avgBedtime'] as String?,
      avgWakeTime: json['avgWakeTime'] as String?,
      bedtimeVarianceMinutes: (json['bedtimeVarianceMinutes'] as num?)?.toDouble(),
      wakeTimeVarianceMinutes: (json['wakeTimeVarianceMinutes'] as num?)?.toDouble(),
      nightsConsidered: json['nightsConsidered'] as int,
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
  final double strainAdjustmentHours;
  final String reasoning;
  final String confidence;
  final List<String> limitations;

  SleepPlanResponse({
    this.recommendedBedtime,
    this.targetWakeTime,
    this.targetSleepHours,
    required this.debtRepaymentHours,
    required this.strainAdjustmentHours,
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
      strainAdjustmentHours: (json['strainAdjustmentHours'] as num?)?.toDouble() ?? 0.0,
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

/// [granularity] is 'day', 'week', or 'month' — beyond a 31-day window the
/// backend returns weekly averages instead of nightly values (and monthly
/// beyond 120), same rollup as ActivityTrendPoint.
class SleepTrendPoint {
  final String date;
  final double totalHours;
  final double deepHours;
  final double remHours;
  final double lightHours;
  final double awakeHours;
  final int sleepScore;
  final String granularity;

  SleepTrendPoint({
    required this.date,
    required this.totalHours,
    required this.deepHours,
    required this.remHours,
    required this.lightHours,
    required this.awakeHours,
    required this.sleepScore,
    required this.granularity,
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
      granularity: json['granularity'] as String? ?? 'day',
    );
  }
}

/// The full sleep-trend response for one window (parity-matrix row 8): the
/// real nightly/weekly/monthly [points] above, plus the account's current
/// personal sleep-duration baseline for a reference line on the chart.
///
/// [baselineHours] is the account's CURRENT rolling baseline (see
/// BaselineService on the backend) — null when the account doesn't have one
/// yet, never a fabricated reference line. It is shown as a single flat
/// line across the whole window, not recomputed per historical point — see
/// [limitations] for that caveat, which the UI must surface visibly (not
/// just in a tooltip), per SleepTrend's Javadoc on the backend.
class SleepTrendResponse {
  final List<SleepTrendPoint> points;
  final double? baselineHours;
  final String? baselineWindowDescription;
  final String? baselineConfidence;
  final int? baselineSampleSize;
  final List<String> limitations;

  SleepTrendResponse({
    required this.points,
    this.baselineHours,
    this.baselineWindowDescription,
    this.baselineConfidence,
    this.baselineSampleSize,
    required this.limitations,
  });

  factory SleepTrendResponse.fromJson(Map<String, dynamic> json) {
    return SleepTrendResponse(
      points: (json['points'] as List)
          .map((e) => SleepTrendPoint.fromJson(e as Map<String, dynamic>))
          .toList(),
      baselineHours: (json['baselineHours'] as num?)?.toDouble(),
      baselineWindowDescription: json['baselineWindowDescription'] as String?,
      baselineConfidence: json['baselineConfidence'] as String?,
      baselineSampleSize: json['baselineSampleSize'] as int?,
      limitations: (json['limitations'] as List).cast<String>(),
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

/// VO2max estimate (parity-matrix row 18). vo2Max/hrMaxBpm/hrRestBpm are
/// null when there isn't enough real data yet — never a fabricated number.
/// See limitations for the published methodology's margin of error and
/// exactly what's missing when the estimate is unavailable.
class Vo2MaxEstimateResponse {
  final double? vo2Max;
  final int? hrMaxBpm;
  final double? hrRestBpm;
  final String? hrMaxSource;
  final String? hrRestSource;
  final String algorithmVersion;
  final String confidence;
  final String methodology;
  final List<String> limitations;

  Vo2MaxEstimateResponse({
    this.vo2Max,
    this.hrMaxBpm,
    this.hrRestBpm,
    this.hrMaxSource,
    this.hrRestSource,
    required this.algorithmVersion,
    required this.confidence,
    required this.methodology,
    required this.limitations,
  });

  factory Vo2MaxEstimateResponse.fromJson(Map<String, dynamic> json) {
    return Vo2MaxEstimateResponse(
      vo2Max: (json['vo2Max'] as num?)?.toDouble(),
      hrMaxBpm: json['hrMaxBpm'] as int?,
      hrRestBpm: (json['hrRestBpm'] as num?)?.toDouble(),
      hrMaxSource: json['hrMaxSource'] as String?,
      hrRestSource: json['hrRestSource'] as String?,
      algorithmVersion: json['algorithmVersion'] as String,
      confidence: json['confidence'] as String,
      methodology: json['methodology'] as String,
      limitations: (json['limitations'] as List).cast<String>(),
    );
  }
}

/// One calendar month's VO2max estimate for the trend view. Months with
/// insufficient real data are omitted entirely, not zero-filled.
class Vo2MaxTrendPointResponse {
  final String month;
  final double vo2Max;

  Vo2MaxTrendPointResponse({required this.month, required this.vo2Max});

  factory Vo2MaxTrendPointResponse.fromJson(Map<String, dynamic> json) {
    return Vo2MaxTrendPointResponse(
      month: json['month'] as String,
      vo2Max: (json['vo2Max'] as num).toDouble(),
    );
  }
}

/// The composite healthspan/wellness score (parity-matrix row 24) — this
/// app's own original composite, not a reproduction of any vendor's
/// proprietary biological-age formula. Both windows use the exact same
/// methodology; only the "recent vs. prior" comparison span differs.
class HealthspanSummaryResponse {
  final HealthspanScoreResponse thirtyDay;
  final HealthspanScoreResponse sixMonth;

  HealthspanSummaryResponse({required this.thirtyDay, required this.sixMonth});

  factory HealthspanSummaryResponse.fromJson(Map<String, dynamic> json) {
    return HealthspanSummaryResponse(
      thirtyDay: HealthspanScoreResponse.fromJson(json['thirtyDay'] as Map<String, dynamic>),
      sixMonth: HealthspanScoreResponse.fromJson(json['sixMonth'] as Map<String, dynamic>),
    );
  }
}

/// A single window's healthspan score. score/factors are null/empty when
/// there isn't enough real history yet across enough contributing metrics —
/// never a fabricated or partial number. confidence never exceeds "medium".
class HealthspanScoreResponse {
  final int? score;
  final String window;
  final String algorithmVersion;
  final String confidence;
  final List<HealthspanFactorResponse> factors;
  final String missingDataTreatment;
  final String methodology;
  final String disclaimer;
  final List<String> limitations;

  HealthspanScoreResponse({
    this.score,
    required this.window,
    required this.algorithmVersion,
    required this.confidence,
    required this.factors,
    required this.missingDataTreatment,
    required this.methodology,
    required this.disclaimer,
    required this.limitations,
  });

  factory HealthspanScoreResponse.fromJson(Map<String, dynamic> json) {
    return HealthspanScoreResponse(
      score: json['score'] as int?,
      window: json['window'] as String,
      algorithmVersion: json['algorithmVersion'] as String,
      confidence: json['confidence'] as String,
      factors: (json['factors'] as List)
          .map((e) => HealthspanFactorResponse.fromJson(e as Map<String, dynamic>))
          .toList(),
      missingDataTreatment: json['missingDataTreatment'] as String,
      methodology: json['methodology'] as String,
      disclaimer: json['disclaimer'] as String,
      limitations: (json['limitations'] as List).cast<String>(),
    );
  }
}

/// One contributing metric's real value, comparison, weight, and
/// contribution to a [HealthspanScoreResponse] — the same
/// cite-your-own-inputs transparency every score in this app follows.
class HealthspanFactorResponse {
  final String name;
  final double? value;
  final String unit;
  final String comparison;
  final String direction;
  final double weight;
  final double contribution;
  final String confidence;
  final String source;

  HealthspanFactorResponse({
    required this.name,
    this.value,
    required this.unit,
    required this.comparison,
    required this.direction,
    required this.weight,
    required this.contribution,
    required this.confidence,
    required this.source,
  });

  factory HealthspanFactorResponse.fromJson(Map<String, dynamic> json) {
    return HealthspanFactorResponse(
      name: json['name'] as String,
      value: (json['value'] as num?)?.toDouble(),
      unit: json['unit'] as String,
      comparison: json['comparison'] as String,
      direction: json['direction'] as String,
      weight: (json['weight'] as num).toDouble(),
      contribution: (json['contribution'] as num).toDouble(),
      confidence: json['confidence'] as String,
      source: json['source'] as String,
    );
  }
}

/// The monthly performance report (parity-matrix row 30) — this app's own
/// original strain/sleep/recovery breakdown for one calendar month, built
/// from real readiness-score history, training load, and sleep-score trend
/// data already computed elsewhere in this app. Not a reproduction of any
/// vendor's proprietary monthly-report design or scoring.
///
/// [strain]/[sleep]/[recovery] are all null when [sufficientHistory] is
/// false — an honest "not enough history yet" state, never a partial report.
class MonthlyReportResponse {
  final String month;
  final String algorithmVersion;
  final bool sufficientHistory;
  final int recoveryScoreCount;
  final int requiredRecoveryScoreCount;
  final MonthlyMetricSectionResponse? strain;
  final MonthlyMetricSectionResponse? sleep;
  final MonthlyMetricSectionResponse? recovery;
  final String overallConfidence;
  final String methodology;
  final List<String> limitations;
  final String? insufficientHistoryMessage;

  MonthlyReportResponse({
    required this.month,
    required this.algorithmVersion,
    required this.sufficientHistory,
    required this.recoveryScoreCount,
    required this.requiredRecoveryScoreCount,
    this.strain,
    this.sleep,
    this.recovery,
    required this.overallConfidence,
    required this.methodology,
    required this.limitations,
    this.insufficientHistoryMessage,
  });

  factory MonthlyReportResponse.fromJson(Map<String, dynamic> json) {
    return MonthlyReportResponse(
      month: json['month'] as String,
      algorithmVersion: json['algorithmVersion'] as String,
      sufficientHistory: json['sufficientHistory'] as bool,
      recoveryScoreCount: json['recoveryScoreCount'] as int,
      requiredRecoveryScoreCount: json['requiredRecoveryScoreCount'] as int,
      strain: json['strain'] != null
          ? MonthlyMetricSectionResponse.fromJson(json['strain'] as Map<String, dynamic>)
          : null,
      sleep: json['sleep'] != null
          ? MonthlyMetricSectionResponse.fromJson(json['sleep'] as Map<String, dynamic>)
          : null,
      recovery: json['recovery'] != null
          ? MonthlyMetricSectionResponse.fromJson(json['recovery'] as Map<String, dynamic>)
          : null,
      overallConfidence: json['overallConfidence'] as String,
      methodology: json['methodology'] as String,
      limitations: (json['limitations'] as List).cast<String>(),
      insufficientHistoryMessage: json['insufficientHistoryMessage'] as String?,
    );
  }
}

/// One dimension's real breakdown (strain, sleep, or recovery) within a
/// [MonthlyReportResponse]. [trendDirection] is "IMPROVING", "DECLINING",
/// "STEADY", or "UNKNOWN" — UNKNOWN when there isn't enough real data this
/// month to read a trend, never a fabricated direction.
class MonthlyMetricSectionResponse {
  final String name;
  final double? averageValue;
  final String unit;
  final int daysWithData;
  final int daysInMonth;
  final String trendDirection;
  final String trendDetail;
  final String confidence;
  final List<String> limitations;

  MonthlyMetricSectionResponse({
    required this.name,
    this.averageValue,
    required this.unit,
    required this.daysWithData,
    required this.daysInMonth,
    required this.trendDirection,
    required this.trendDetail,
    required this.confidence,
    required this.limitations,
  });

  factory MonthlyMetricSectionResponse.fromJson(Map<String, dynamic> json) {
    return MonthlyMetricSectionResponse(
      name: json['name'] as String,
      averageValue: (json['averageValue'] as num?)?.toDouble(),
      unit: json['unit'] as String,
      daysWithData: json['daysWithData'] as int,
      daysInMonth: json['daysInMonth'] as int,
      trendDirection: json['trendDirection'] as String,
      trendDetail: json['trendDetail'] as String,
      confidence: json['confidence'] as String,
      limitations: (json['limitations'] as List).cast<String>(),
    );
  }
}

/// A point within a step-count trend view. Calories/active minutes aren't
/// tracked in the current data model — see ActivitySummary.
///
/// [granularity] is 'day', 'week', or 'month' — beyond a 31-day window the
/// backend returns weekly averages instead of daily counts (and monthly
/// beyond 120), so a large window renders as a readable trend instead of
/// hundreds of raw daily bars. [steps] is that day's real count for 'day',
/// or the average steps/day across real days in the bucket for 'week'/'month'.
class ActivityTrendPoint {
  final String date;
  final int steps;
  final String granularity;

  ActivityTrendPoint({
    required this.date,
    required this.steps,
    required this.granularity,
  });

  factory ActivityTrendPoint.fromJson(Map<String, dynamic> json) {
    return ActivityTrendPoint(
      date: json['date'] as String,
      steps: json['steps'] as int,
      granularity: json['granularity'] as String? ?? 'day',
    );
  }
}

/// The full step-trend response for one window (parity-matrix row 8): the
/// real daily/weekly/monthly [points] above, plus the account's current
/// personal steps baseline for a reference line on the chart.
///
/// [baselineSteps] is the account's CURRENT rolling baseline (see
/// BaselineService on the backend) — null when the account doesn't have one
/// yet, never a fabricated reference line. It is shown as a single flat
/// line across the whole window, not recomputed per historical point — see
/// [limitations] for that caveat, which the UI must surface visibly (not
/// just in a tooltip), per ActivityTrend's Javadoc on the backend.
class ActivityTrendResponse {
  final List<ActivityTrendPoint> points;
  final double? baselineSteps;
  final String? baselineWindowDescription;
  final String? baselineConfidence;
  final int? baselineSampleSize;
  final List<String> limitations;

  ActivityTrendResponse({
    required this.points,
    this.baselineSteps,
    this.baselineWindowDescription,
    this.baselineConfidence,
    this.baselineSampleSize,
    required this.limitations,
  });

  factory ActivityTrendResponse.fromJson(Map<String, dynamic> json) {
    return ActivityTrendResponse(
      points: (json['points'] as List)
          .map((e) => ActivityTrendPoint.fromJson(e as Map<String, dynamic>))
          .toList(),
      baselineSteps: (json['baselineSteps'] as num?)?.toDouble(),
      baselineWindowDescription: json['baselineWindowDescription'] as String?,
      baselineConfidence: json['baselineConfidence'] as String?,
      baselineSampleSize: json['baselineSampleSize'] as int?,
      limitations: (json['limitations'] as List).cast<String>(),
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

/// Same shape as [BehaviorCorrelationResponse], but against a Garmin-
/// exclusive signal (Body Battery, Garmin's own Training Readiness) instead
/// of this app's own readiness score.
class GarminSignalCorrelationResponse {
  final String signalName;
  final String category;
  final String behavior;
  final int loggedDayCount;
  final int notLoggedDayCount;
  final double avgSignalWhenLogged;
  final double avgSignalWhenNotLogged;
  final double difference;
  final String confidence;
  final List<String> limitations;

  GarminSignalCorrelationResponse({
    required this.signalName,
    required this.category,
    required this.behavior,
    required this.loggedDayCount,
    required this.notLoggedDayCount,
    required this.avgSignalWhenLogged,
    required this.avgSignalWhenNotLogged,
    required this.difference,
    required this.confidence,
    required this.limitations,
  });

  factory GarminSignalCorrelationResponse.fromJson(Map<String, dynamic> json) {
    return GarminSignalCorrelationResponse(
      signalName: json['signalName'] as String,
      category: json['category'] as String,
      behavior: json['behavior'] as String,
      loggedDayCount: json['loggedDayCount'] as int,
      notLoggedDayCount: json['notLoggedDayCount'] as int,
      avgSignalWhenLogged: (json['avgSignalWhenLogged'] as num).toDouble(),
      avgSignalWhenNotLogged: (json['avgSignalWhenNotLogged'] as num).toDouble(),
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

/// The real Garmin Connect connection state — a from-scratch Java port of
/// Garmin's unofficial mobile-app login flow, used to pull real historical
/// data directly from the user's own Garmin Connect account. See
/// GarminConnectAuthClient on the backend for why this exists.
class GarminConnectStatusResponse {
  final String status; // disconnected/mfa_required/connected/error
  final String? email;
  final String? lastError;
  final String? connectedAt;
  final String? lastSyncAt;

  GarminConnectStatusResponse({
    required this.status,
    this.email,
    this.lastError,
    this.connectedAt,
    this.lastSyncAt,
  });

  factory GarminConnectStatusResponse.fromJson(Map<String, dynamic> json) {
    return GarminConnectStatusResponse(
      status: json['status'] as String,
      email: json['email'] as String?,
      lastError: json['lastError'] as String?,
      connectedAt: json['connectedAt'] as String?,
      lastSyncAt: json['lastSyncAt'] as String?,
    );
  }
}

/// Result of a login or MFA-submission attempt.
class GarminConnectConnectResponse {
  final String status; // connected/mfa_required/error
  final String? email;
  final String? mfaMethod;
  final String? message;

  GarminConnectConnectResponse({
    required this.status,
    this.email,
    this.mfaMethod,
    this.message,
  });

  factory GarminConnectConnectResponse.fromJson(Map<String, dynamic> json) {
    return GarminConnectConnectResponse(
      status: json['status'] as String,
      email: json['email'] as String?,
      mfaMethod: json['mfaMethod'] as String?,
      message: json['message'] as String?,
    );
  }
}

/// Result of a historical sync run.
class GarminConnectSyncResultResponse {
  final int daysAttempted;
  final int daysWithData;
  final int measurementsWritten;
  final List<String> errors;

  GarminConnectSyncResultResponse({
    required this.daysAttempted,
    required this.daysWithData,
    required this.measurementsWritten,
    required this.errors,
  });

  factory GarminConnectSyncResultResponse.fromJson(Map<String, dynamic> json) {
    return GarminConnectSyncResultResponse(
      daysAttempted: json['daysAttempted'] as int,
      daysWithData: json['daysWithData'] as int,
      measurementsWritten: json['measurementsWritten'] as int,
      errors: (json['errors'] as List).cast<String>(),
    );
  }
}

/// Garmin-exclusive Body Battery + Training Readiness — deliberately kept
/// separate from this app's own readiness score. "As of" dates are shown
/// because these are daily aggregates that lag behind real time, not live
/// values — see GarminEnrichmentService.
class GarminEnrichmentResponse {
  final String? bodyBatteryAsOf;
  final double? bodyBatteryHigh;
  final double? bodyBatteryLow;
  final double? bodyBatteryCharged;
  final double? bodyBatteryDrained;
  final String? trainingReadinessAsOf;
  final int? garminTrainingReadiness;

  GarminEnrichmentResponse({
    this.bodyBatteryAsOf,
    this.bodyBatteryHigh,
    this.bodyBatteryLow,
    this.bodyBatteryCharged,
    this.bodyBatteryDrained,
    this.trainingReadinessAsOf,
    this.garminTrainingReadiness,
  });

  bool get hasBodyBattery => bodyBatteryHigh != null || bodyBatteryLow != null;
  bool get hasTrainingReadiness => garminTrainingReadiness != null;

  factory GarminEnrichmentResponse.fromJson(Map<String, dynamic> json) {
    return GarminEnrichmentResponse(
      bodyBatteryAsOf: json['bodyBatteryAsOf'] as String?,
      bodyBatteryHigh: (json['bodyBatteryHigh'] as num?)?.toDouble(),
      bodyBatteryLow: (json['bodyBatteryLow'] as num?)?.toDouble(),
      bodyBatteryCharged: (json['bodyBatteryCharged'] as num?)?.toDouble(),
      bodyBatteryDrained: (json['bodyBatteryDrained'] as num?)?.toDouble(),
      trainingReadinessAsOf: json['trainingReadinessAsOf'] as String?,
      garminTrainingReadiness: json['garminTrainingReadiness'] as int?,
    );
  }
}

class BodyBatteryTrendPointResponse {
  final String date;
  final double? high;
  final double? low;

  BodyBatteryTrendPointResponse({required this.date, this.high, this.low});

  factory BodyBatteryTrendPointResponse.fromJson(Map<String, dynamic> json) {
    return BodyBatteryTrendPointResponse(
      date: json['date'] as String,
      high: (json['high'] as num?)?.toDouble(),
      low: (json['low'] as num?)?.toDouble(),
    );
  }
}

/// One imported blood biomarker (lab bloodwork) reading. [referenceLow]/
/// [referenceHigh]/[inRange] are only ever populated from the user's own
/// CSV row — never a fabricated "typical" range (see the backend's
/// biomarkers module Javadoc for why). [inRange] is null when no reference
/// range was supplied at all — an honest "unknown", not a default true.
class BiomarkerReadingResponse {
  final int id;
  final String biomarkerName;
  final String? category;
  final double value;
  final String unit;
  final double? referenceLow;
  final double? referenceHigh;
  final bool? inRange;
  final String readingDate;

  BiomarkerReadingResponse({
    required this.id,
    required this.biomarkerName,
    this.category,
    required this.value,
    required this.unit,
    this.referenceLow,
    this.referenceHigh,
    this.inRange,
    required this.readingDate,
  });

  bool get hasReferenceRange => referenceLow != null || referenceHigh != null;

  factory BiomarkerReadingResponse.fromJson(Map<String, dynamic> json) {
    return BiomarkerReadingResponse(
      id: json['id'] as int,
      biomarkerName: json['biomarkerName'] as String,
      category: json['category'] as String?,
      value: (json['value'] as num).toDouble(),
      unit: json['unit'] as String,
      referenceLow: (json['referenceLow'] as num?)?.toDouble(),
      referenceHigh: (json['referenceHigh'] as num?)?.toDouble(),
      inRange: json['inRange'] as bool?,
      readingDate: json['readingDate'] as String,
    );
  }
}

/// Static reference-catalog entry (display labeling only — no numeric range).
class BiomarkerReferenceEntryResponse {
  final String name;
  final String category;
  final String description;

  BiomarkerReferenceEntryResponse({required this.name, required this.category, required this.description});

  factory BiomarkerReferenceEntryResponse.fromJson(Map<String, dynamic> json) {
    return BiomarkerReferenceEntryResponse(
      name: json['name'] as String,
      category: json['category'] as String,
      description: json['description'] as String,
    );
  }
}

/// One set to submit when logging a workout — see [ApiClient.logStrengthWorkout].
class StrengthSetInput {
  final int reps;
  final double? weightKg;

  StrengthSetInput({required this.reps, this.weightKg});

  Map<String, dynamic> toJson() => {'reps': reps, 'weightKg': weightKg};
}

/// One exercise (with its sets) to submit when logging a workout.
class StrengthExerciseInput {
  final String exerciseName;
  final List<StrengthSetInput> sets;

  StrengthExerciseInput({required this.exerciseName, required this.sets});

  Map<String, dynamic> toJson() => {
        'exerciseName': exerciseName,
        'sets': sets.map((s) => s.toJson()).toList(),
      };
}

/// A logged set, as returned by the API (setOrder is workout-wide, 1-based).
class StrengthSetResponse {
  final int setOrder;
  final int reps;
  final double? weightKg;

  StrengthSetResponse({required this.setOrder, required this.reps, this.weightKg});

  factory StrengthSetResponse.fromJson(Map<String, dynamic> json) {
    return StrengthSetResponse(
      setOrder: json['setOrder'] as int,
      reps: json['reps'] as int,
      weightKg: (json['weightKg'] as num?)?.toDouble(),
    );
  }
}

/// One exercise within a logged workout, with its sets in performed order.
class StrengthExerciseResponse {
  final String exerciseName;
  final List<StrengthSetResponse> sets;

  StrengthExerciseResponse({required this.exerciseName, required this.sets});

  factory StrengthExerciseResponse.fromJson(Map<String, dynamic> json) {
    return StrengthExerciseResponse(
      exerciseName: json['exerciseName'] as String,
      sets: (json['sets'] as List).map((e) => StrengthSetResponse.fromJson(e as Map<String, dynamic>)).toList(),
    );
  }
}

/// A manually-logged strength workout. [durationIsEstimated] is true when
/// [durationMinutes] wasn't user-reported and was instead estimated from
/// set count (see StrengthTrainingService) — never silently treated as a
/// measured duration.
class StrengthWorkoutResponse {
  final int id;
  final String startedAt;
  final int? userDurationMinutes;
  final int durationMinutes;
  final bool durationIsEstimated;
  final String? note;
  final List<StrengthExerciseResponse> exercises;

  StrengthWorkoutResponse({
    required this.id,
    required this.startedAt,
    this.userDurationMinutes,
    required this.durationMinutes,
    required this.durationIsEstimated,
    this.note,
    required this.exercises,
  });

  int get totalSets => exercises.fold(0, (sum, e) => sum + e.sets.length);

  factory StrengthWorkoutResponse.fromJson(Map<String, dynamic> json) {
    return StrengthWorkoutResponse(
      id: json['id'] as int,
      startedAt: json['startedAt'] as String,
      userDurationMinutes: json['userDurationMinutes'] as int?,
      durationMinutes: json['durationMinutes'] as int,
      durationIsEstimated: json['durationIsEstimated'] as bool,
      note: json['note'] as String?,
      exercises: (json['exercises'] as List)
          .map((e) => StrengthExerciseResponse.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

/// One bucket of the Strength Activity Time trend — see
/// StrengthActivityTrendPoint on the backend for the full honesty
/// convention behind garminMinutes vs manualMinutes never being blended
/// into one unlabeled number.
class StrengthActivityTrendPointResponse {
  final String periodStart;
  final String periodEnd;
  final double garminMinutes;
  final double manualMinutes;
  final bool manualMinutesEstimated;
  final double totalMinutes;

  StrengthActivityTrendPointResponse({
    required this.periodStart,
    required this.periodEnd,
    required this.garminMinutes,
    required this.manualMinutes,
    required this.manualMinutesEstimated,
    required this.totalMinutes,
  });

  factory StrengthActivityTrendPointResponse.fromJson(Map<String, dynamic> json) {
    return StrengthActivityTrendPointResponse(
      periodStart: json['periodStart'] as String,
      periodEnd: json['periodEnd'] as String,
      garminMinutes: (json['garminMinutes'] as num).toDouble(),
      manualMinutes: (json['manualMinutes'] as num).toDouble(),
      manualMinutesEstimated: json['manualMinutesEstimated'] as bool,
      totalMinutes: (json['totalMinutes'] as num).toDouble(),
    );
  }
}

/// The full Strength Activity Time trend response for one window.
class StrengthActivityTrendResponse {
  final String window;
  final List<StrengthActivityTrendPointResponse> points;
  final int? weeklyGoalMinutes;
  final List<String> limitations;

  StrengthActivityTrendResponse({
    required this.window,
    required this.points,
    this.weeklyGoalMinutes,
    required this.limitations,
  });

  factory StrengthActivityTrendResponse.fromJson(Map<String, dynamic> json) {
    return StrengthActivityTrendResponse(
      window: json['window'] as String,
      points: (json['points'] as List)
          .map((e) => StrengthActivityTrendPointResponse.fromJson(e as Map<String, dynamic>))
          .toList(),
      weeklyGoalMinutes: json['weeklyGoalMinutes'] as int?,
      limitations: (json['limitations'] as List).cast<String>(),
    );
  }
}

/// A deterministically-generated workout (parity-matrix row 26) — see
/// WorkoutGeneratorService on the backend. [source] is always
/// 'DETERMINISTIC_TEMPLATE' today; no LLM path is wired in this app yet.
class GeneratedWorkoutResponse {
  final String algorithmVersion;
  final String source;
  final String goal;
  final List<String> equipmentConsidered;
  final List<String> injuryLimitationsConsidered;
  final int durationMinutesRequested;
  final int durationMinutesEstimated;
  final List<WorkoutExerciseResponse> warmup;
  final List<WorkoutExerciseResponse> main;
  final List<WorkoutExerciseResponse> cooldown;
  final String? intensityAdjustment;
  final String disclaimer;
  final List<String> limitations;
  final String generatedAt;

  GeneratedWorkoutResponse({
    required this.algorithmVersion,
    required this.source,
    required this.goal,
    required this.equipmentConsidered,
    required this.injuryLimitationsConsidered,
    required this.durationMinutesRequested,
    required this.durationMinutesEstimated,
    required this.warmup,
    required this.main,
    required this.cooldown,
    this.intensityAdjustment,
    required this.disclaimer,
    required this.limitations,
    required this.generatedAt,
  });

  factory GeneratedWorkoutResponse.fromJson(Map<String, dynamic> json) {
    List<WorkoutExerciseResponse> parseExercises(String key) => (json[key] as List)
        .map((e) => WorkoutExerciseResponse.fromJson(e as Map<String, dynamic>))
        .toList();
    return GeneratedWorkoutResponse(
      algorithmVersion: json['algorithmVersion'] as String,
      source: json['source'] as String,
      goal: json['goal'] as String,
      equipmentConsidered: (json['equipmentConsidered'] as List).cast<String>(),
      injuryLimitationsConsidered: (json['injuryLimitationsConsidered'] as List).cast<String>(),
      durationMinutesRequested: json['durationMinutesRequested'] as int,
      durationMinutesEstimated: json['durationMinutesEstimated'] as int,
      warmup: parseExercises('warmup'),
      main: parseExercises('main'),
      cooldown: parseExercises('cooldown'),
      intensityAdjustment: json['intensityAdjustment'] as String?,
      disclaimer: json['disclaimer'] as String,
      limitations: (json['limitations'] as List).cast<String>(),
      generatedAt: json['generatedAt'] as String,
    );
  }
}

class WorkoutExerciseResponse {
  final String name;
  final int sets;
  final String repsOrDuration;
  final int restSeconds;
  final String equipment;

  WorkoutExerciseResponse({
    required this.name,
    required this.sets,
    required this.repsOrDuration,
    required this.restSeconds,
    required this.equipment,
  });

  factory WorkoutExerciseResponse.fromJson(Map<String, dynamic> json) {
    return WorkoutExerciseResponse(
      name: json['name'] as String,
      sets: json['sets'] as int,
      repsOrDuration: json['repsOrDuration'] as String,
      restSeconds: json['restSeconds'] as int,
      equipment: json['equipment'] as String,
    );
  }
}
