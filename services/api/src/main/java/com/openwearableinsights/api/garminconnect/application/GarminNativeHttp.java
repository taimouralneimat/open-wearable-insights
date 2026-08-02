package com.openwearableinsights.api.garminconnect.application;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared low-level plumbing for talking to Garmin Connect's native mobile-app
 * API — the exact headers Garmin's own Android app sends, used by both the
 * auth client (login/token exchange) and the data client (historical sync)
 * so the two never silently drift apart.
 */
final class GarminNativeHttp {

    private static final String NATIVE_API_USER_AGENT = "GCM-Android-5.23";
    private static final String NATIVE_X_GARMIN_USER_AGENT =
            "com.garmin.android.apps.connectmobile/5.23; ; Google/sdk_gphone64_arm64/google; "
            + "Android/33; Dalvik/2.1.0";

    private GarminNativeHttp() {}

    static String[] headers(Map<String, String> extra) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", NATIVE_API_USER_AGENT);
        headers.put("X-Garmin-User-Agent", NATIVE_X_GARMIN_USER_AGENT);
        headers.put("X-Garmin-Paired-App-Version", "10861");
        headers.put("X-Garmin-Client-Platform", "Android");
        headers.put("X-App-Ver", "10861");
        headers.put("X-Lang", "en");
        headers.put("X-GCExperience", "GC5");
        headers.put("Accept-Language", "en-US,en;q=0.9");
        headers.putAll(extra);
        return headers.entrySet().stream()
                .flatMap(e -> java.util.stream.Stream.of(e.getKey(), e.getValue()))
                .toArray(String[]::new);
    }

    static String basicAuth(String clientId) {
        return "Basic " + Base64.getEncoder().encodeToString((clientId + ":").getBytes(StandardCharsets.UTF_8));
    }

    static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
