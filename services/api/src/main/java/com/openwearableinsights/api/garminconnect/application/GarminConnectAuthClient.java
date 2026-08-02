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
import java.util.List;
import java.util.Map;

/**
 * From-scratch Java port of Garmin Connect's unofficial mobile-app SSO/OAuth2
 * login flow.
 *
 * <p>Why this exists: Garmin's official Health API requires a registered
 * legal entity and is closed to new individual applicants, and Garmin
 * Express's local sync folder only ever holds a transient upload buffer
 * (cleared once it's pushed to the cloud) — neither can deliver a user's
 * actual historical wellness data. The only path that does is logging into
 * the user's own Garmin Connect account the same way Garmin's own mobile
 * app does, using the user's own credentials, entered by the user in this
 * app's own "Garmin Connect" connector screen — never handled by anyone but
 * the user and this client.
 *
 * <p>Faithful to the "mobile+requests" strategy of the actively-maintained
 * reference implementation cyberjunky/python-garminconnect (its predecessor,
 * matin/garth, is now deprecated — Garmin changed its auth flow and garth's
 * maintainer stopped adapting to it). That library actually tries 5
 * strategies in a cascading chain; 3 of them (mobile+cffi, widget+cffi,
 * portal+cffi) exist purely to rotate TLS fingerprints past Cloudflare bot
 * detection via curl_cffi, which the JVM's TLS stack cannot replicate
 * without a custom SSL engine. This client implements only the plain-HTTP
 * "mobile+requests" strategy — a straightforward JSON POST, no TLS
 * fingerprinting involved. If Garmin's Cloudflare layer starts blocking
 * plain-HTTP mobile logins outright, the remaining non-cffi fallback
 * (portal+requests) or true TLS-fingerprint impersonation would be the next
 * step — not attempted here since it's unverified whether it's actually
 * necessary.
 *
 * <p>Only OAuth tokens are ever persisted (see GarminConnectAccountRepository)
 * — the password is used exactly once, in-memory, to build the login
 * request, and is never logged or stored.
 */
@Component
public class GarminConnectAuthClient {

    private static final Logger log = LoggerFactory.getLogger(GarminConnectAuthClient.class);

    private static final String SSO_BASE = "https://sso.garmin.com";
    private static final String IOS_SSO_CLIENT_ID = "GCM_IOS_DARK";
    private static final String IOS_SERVICE_URL = "https://mobile.integration.garmin.com/gcm/ios";
    private static final String IOS_LOGIN_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148";

    private static final String DI_TOKEN_URL = "https://diauth.garmin.com/di-oauth2-service/oauth/token";
    private static final String DI_GRANT_TYPE =
            "https://connectapi.garmin.com/di-oauth2-service/oauth/grant/service_ticket";
    private static final List<String> DI_CLIENT_IDS = List.of(
            "GARMIN_CONNECT_MOBILE_ANDROID_DI_2025Q2",
            "GARMIN_CONNECT_MOBILE_ANDROID_DI_2024Q4",
            "GARMIN_CONNECT_MOBILE_ANDROID_DI",
            "GARMIN_CONNECT_MOBILE_IOS_DI"
    );

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Attempt login. Returns one of: {@link LoginOutcome.Authenticated} with
     * real bearer tokens, {@link LoginOutcome.MfaRequired} carrying the
     * server-side state needed to complete {@link #submitMfa}, or
     * {@link LoginOutcome.Failed} with a reason.
     */
    public LoginOutcome login(String email, String password) {
        CookieManager cookieManager = new CookieManager();
        HttpClient http = buildClient(cookieManager);
        try {
            HttpResponse<String> response = http.send(loginRequest(email, password), HttpResponse.BodyHandlers.ofString());
            return handleSsoResponse(response, email, cookieManager, IOS_SERVICE_URL, "ios");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Garmin Connect login request failed: {}", e.getMessage());
            return new LoginOutcome.Failed("Could not reach Garmin Connect: " + e.getMessage(), false, false);
        }
    }

    /** Complete a login that returned {@link LoginOutcome.MfaRequired}, given the code the user entered. */
    public LoginOutcome submitMfa(PendingMfa pending, String code) {
        HttpClient http = buildClient(pending.cookieManager());
        Map<String, Object> body = Map.of(
                "mfaMethod", pending.mfaMethod(),
                "mfaVerificationCode", code,
                "rememberMyBrowser", true,
                "reconsentList", List.of(),
                "mfaSetup", false
        );
        try {
            String json = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SSO_BASE + "/mobile/api/mfa/verifyCode"
                            + "?clientId=" + IOS_SSO_CLIENT_ID + "&locale=en-US&service=" + GarminNativeHttp.urlEncode(IOS_SERVICE_URL)))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", IOS_LOGIN_UA)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Content-Type", "application/json")
                    .header("Origin", SSO_BASE)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return handleSsoResponse(response, pending.email(), pending.cookieManager(), IOS_SERVICE_URL, "ios");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Garmin Connect MFA verification failed: {}", e.getMessage());
            return new LoginOutcome.Failed("Could not verify MFA code: " + e.getMessage(), false, false);
        }
    }

    /** Refresh an expired DI bearer token using the stored refresh token. */
    public GarminTokens refresh(GarminTokens expired) {
        HttpClient http = buildClient(new CookieManager());
        String form = "grant_type=refresh_token"
                + "&client_id=" + GarminNativeHttp.urlEncode(expired.diClientId())
                + "&refresh_token=" + GarminNativeHttp.urlEncode(expired.diRefreshToken());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DI_TOKEN_URL))
                .timeout(Duration.ofSeconds(30))
                .headers(GarminNativeHttp.headers(Map.of(
                        "Authorization", GarminNativeHttp.basicAuth(expired.diClientId()),
                        "Accept", "application/json",
                        "Content-Type", "application/x-www-form-urlencoded",
                        "Cache-Control", "no-cache"
                )))
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new GarminConnectAuthException("DI token refresh failed: HTTP " + response.statusCode());
            }
            JsonNode json = mapper.readTree(response.body());
            String accessToken = json.path("access_token").asText(null);
            if (accessToken == null) {
                throw new GarminConnectAuthException("DI token refresh response missing access_token");
            }
            String refreshToken = json.hasNonNull("refresh_token") ? json.get("refresh_token").asText() : expired.diRefreshToken();
            return new GarminTokens(accessToken, refreshToken, expired.diClientId());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new GarminConnectAuthException("DI token refresh failed: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ //

    private HttpRequest loginRequest(String email, String password) throws IOException {
        Map<String, Object> body = Map.of(
                "username", email,
                "password", password,
                "rememberMe", true,
                "captchaToken", ""
        );
        String json = mapper.writeValueAsString(body);
        return HttpRequest.newBuilder()
                .uri(URI.create(SSO_BASE + "/mobile/api/login"
                        + "?clientId=" + IOS_SSO_CLIENT_ID + "&locale=en-US&service=" + GarminNativeHttp.urlEncode(IOS_SERVICE_URL)))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", IOS_LOGIN_UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Content-Type", "application/json")
                .header("Origin", SSO_BASE)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
    }

    private LoginOutcome handleSsoResponse(
            HttpResponse<String> response, String email, CookieManager cookieManager, String serviceUrl, String flow
    ) {
        SsoParseResult parsed = parseSsoResponse(response.statusCode(), response.body());
        return switch (parsed) {
            case SsoParseResult.Successful s -> {
                try {
                    GarminTokens tokens = exchangeServiceTicket(s.serviceTicketId(), serviceUrl);
                    yield new LoginOutcome.Authenticated(tokens);
                } catch (GarminConnectAuthException e) {
                    yield new LoginOutcome.Failed(e.getMessage(), false, false);
                }
            }
            case SsoParseResult.MfaRequired m ->
                    new LoginOutcome.MfaRequired(new PendingMfa(email, m.mfaMethod(), cookieManager));
            case SsoParseResult.Failed f -> new LoginOutcome.Failed(f.message(), f.rateLimited(), f.invalidCredentials());
        };
    }

    /**
     * Pure parsing of Garmin's SSO login/MFA response — no I/O, so this is
     * unit-testable against real (fixture) JSON shapes without hitting
     * Garmin's actual servers (which would risk rate-limiting/flagging the
     * real account this is meant to serve).
     */
    sealed interface SsoParseResult {
        record Successful(String serviceTicketId) implements SsoParseResult {}
        record MfaRequired(String mfaMethod) implements SsoParseResult {}
        record Failed(String message, boolean rateLimited, boolean invalidCredentials) implements SsoParseResult {}
    }

    SsoParseResult parseSsoResponse(int statusCode, String body) {
        if (statusCode == 429) {
            return new SsoParseResult.Failed("Garmin rate-limited this login attempt (HTTP 429) — try again later.", true, false);
        }
        if (statusCode == 403) {
            return new SsoParseResult.Failed("Garmin returned HTTP 403 (bot challenge) — try again in a few minutes.", false, false);
        }

        JsonNode json;
        try {
            json = mapper.readTree(body);
        } catch (IOException e) {
            return new SsoParseResult.Failed("Garmin returned a non-JSON response (HTTP " + statusCode + ")", false, false);
        }

        String type = json.path("responseStatus").path("type").asText("");
        switch (type) {
            case "SUCCESSFUL" -> {
                String ticket = json.path("serviceTicketId").asText(null);
                if (ticket == null) {
                    return new SsoParseResult.Failed("Garmin reported success but returned no service ticket", false, false);
                }
                return new SsoParseResult.Successful(ticket);
            }
            case "MFA_REQUIRED" -> {
                String mfaMethod = json.path("customerMfaInfo").path("mfaLastMethodUsed").asText("email");
                return new SsoParseResult.MfaRequired(mfaMethod);
            }
            case "INVALID_USERNAME_PASSWORD" -> {
                return new SsoParseResult.Failed("Incorrect Garmin Connect username or password.", false, true);
            }
            case "CAPTCHA_REQUIRED" -> {
                return new SsoParseResult.Failed("Garmin is asking for a CAPTCHA (bot challenge) — try again later, "
                        + "or log in once at connect.garmin.com from this network first.", false, false);
            }
            default -> {
                if ("429".equals(json.path("error").path("status-code").asText(""))) {
                    return new SsoParseResult.Failed("Garmin rate-limited this login attempt — try again later.", true, false);
                }
                return new SsoParseResult.Failed("Unexpected response from Garmin: " + json, false, false);
            }
        }
    }

    private GarminTokens exchangeServiceTicket(String ticket, String serviceUrl) {
        HttpClient http = buildClient(new CookieManager());
        for (String clientId : DI_CLIENT_IDS) {
            String form = "client_id=" + GarminNativeHttp.urlEncode(clientId)
                    + "&service_ticket=" + GarminNativeHttp.urlEncode(ticket)
                    + "&grant_type=" + GarminNativeHttp.urlEncode(DI_GRANT_TYPE)
                    + "&service_url=" + GarminNativeHttp.urlEncode(serviceUrl);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DI_TOKEN_URL))
                    .timeout(Duration.ofSeconds(30))
                    .headers(GarminNativeHttp.headers(Map.of(
                            "Authorization", GarminNativeHttp.basicAuth(clientId),
                            "Accept", "application/json,text/html;q=0.9,*/*;q=0.8",
                            "Content-Type", "application/x-www-form-urlencoded",
                            "Cache-Control", "no-cache"
                    )))
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 429) {
                    throw new GarminConnectAuthException("DI token exchange rate limited (HTTP 429)");
                }
                if (response.statusCode() / 100 != 2) {
                    log.debug("DI exchange failed for client_id={}: HTTP {}", clientId, response.statusCode());
                    continue;
                }
                JsonNode json = mapper.readTree(response.body());
                String accessToken = json.path("access_token").asText(null);
                if (accessToken == null) continue;
                String refreshToken = json.hasNonNull("refresh_token") ? json.get("refresh_token").asText() : null;
                return new GarminTokens(accessToken, refreshToken, clientId);
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                log.debug("DI exchange transport error for client_id={}: {}", clientId, e.getMessage());
            }
        }
        throw new GarminConnectAuthException("Logged in, but Garmin rejected the token exchange for every known client ID.");
    }

    private static HttpClient buildClient(CookieManager cookieManager) {
        return HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** Outcome of a login or MFA-submission attempt. */
    public sealed interface LoginOutcome {
        record Authenticated(GarminTokens tokens) implements LoginOutcome {}
        record MfaRequired(PendingMfa pending) implements LoginOutcome {}
        record Failed(String message, boolean rateLimited, boolean invalidCredentials) implements LoginOutcome {}
    }

    /**
     * Server-side-only state needed to complete an in-progress MFA challenge.
     * Never serialized to the client — the REST layer hands back an opaque
     * challenge id and keeps this in memory (see GarminConnectService).
     */
    public record PendingMfa(String email, String mfaMethod, CookieManager cookieManager) {}

    public static class GarminConnectAuthException extends RuntimeException {
        public GarminConnectAuthException(String message) { super(message); }
        public GarminConnectAuthException(String message, Throwable cause) { super(message, cause); }
    }
}
