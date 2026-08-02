package com.openwearableinsights.api.garminconnect.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

/**
 * Fetches a day's real wellness data from Garmin Connect's API tier
 * (connectapi.garmin.com) using a DI bearer token obtained via
 * {@link GarminConnectAuthClient}. Read-only — GET requests against the
 * same endpoints Garmin's own mobile app calls.
 */
@Component
class GarminConnectDataClient {

    private static final Logger log = LoggerFactory.getLogger(GarminConnectDataClient.class);
    private static final String CONNECT_API = "https://connectapi.garmin.com";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /** GET /userprofile-service/socialProfile — used both to validate a token and to get displayName. */
    JsonNode fetchSocialProfile(GarminTokens tokens) throws GarminConnectApiException {
        return get("/userprofile-service/socialProfile", tokens);
    }

    /** GET the daily wellness summary (steps, resting HR, average stress, etc.) for one date. */
    JsonNode fetchDailySummary(GarminTokens tokens, String displayName, LocalDate date) throws GarminConnectApiException {
        return get("/usersummary-service/usersummary/daily/" + GarminNativeHttp.urlEncode(displayName)
                + "?calendarDate=" + date, tokens);
    }

    /** GET the sleep summary (dailySleepDTO: deep/light/rem/awake seconds, start/end) for one date. */
    JsonNode fetchSleepData(GarminTokens tokens, String displayName, LocalDate date) throws GarminConnectApiException {
        return get("/wellness-service/wellness/dailySleepData/" + GarminNativeHttp.urlEncode(displayName)
                + "?date=" + date + "&nonSleepBufferMinutes=60", tokens);
    }

    /** GET the HRV summary (last night's average, in ms) for one date. Garmin returns 204/empty if unavailable. */
    JsonNode fetchHrvData(GarminTokens tokens, LocalDate date) throws GarminConnectApiException {
        return get("/hrv-service/hrv/" + date, tokens);
    }

    private JsonNode get(String path, GarminTokens tokens) throws GarminConnectApiException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CONNECT_API + path))
                .timeout(Duration.ofSeconds(30))
                .headers(GarminNativeHttp.headers(Map.of(
                        "Authorization", "Bearer " + tokens.diToken(),
                        "Accept", "application/json"
                )))
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new GarminConnectApiException(path, response.statusCode(), true);
            }
            if (response.statusCode() == 429) {
                throw new GarminConnectApiException(path, 429, false);
            }
            if (response.statusCode() == 204 || response.body() == null || response.body().isBlank()) {
                return mapper.nullNode();
            }
            if (response.statusCode() / 100 != 2) {
                throw new GarminConnectApiException(path, response.statusCode(), false);
            }
            return mapper.readTree(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Garmin Connect API call to {} failed: {}", path, e.getMessage());
            throw new GarminConnectApiException(path, -1, false, e);
        }
    }

    static class GarminConnectApiException extends Exception {
        final String path;
        final int statusCode;
        final boolean tokenExpired;

        GarminConnectApiException(String path, int statusCode, boolean tokenExpired) {
            super("Garmin Connect API " + path + " returned HTTP " + statusCode);
            this.path = path;
            this.statusCode = statusCode;
            this.tokenExpired = tokenExpired;
        }

        GarminConnectApiException(String path, int statusCode, boolean tokenExpired, Throwable cause) {
            super("Garmin Connect API " + path + " failed: " + cause.getMessage(), cause);
            this.path = path;
            this.statusCode = statusCode;
            this.tokenExpired = tokenExpired;
        }
    }
}
