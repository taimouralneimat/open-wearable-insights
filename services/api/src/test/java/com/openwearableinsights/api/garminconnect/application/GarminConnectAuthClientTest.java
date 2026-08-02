package com.openwearableinsights.api.garminconnect.application;

import com.openwearableinsights.api.garminconnect.application.GarminConnectAuthClient.SsoParseResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GarminConnectAuthClient#parseSsoResponse}, the pure
 * (no-I/O) parsing of Garmin's real SSO login/MFA JSON response shapes —
 * field names taken from the actively-maintained reference implementation
 * cyberjunky/python-garminconnect (matin/garth, the historical reference, is
 * now deprecated after Garmin changed its auth flow).
 *
 * <p>Deliberately does not hit Garmin's real login endpoint from a test —
 * that would risk rate-limiting or bot-flagging whatever IP runs this suite,
 * for a login attempt that isn't testing anything Garmin controls anyway.
 * The one real network call this feature has been verified against (a live
 * login attempt with a fake, nonexistent account, from this same client)
 * returned exactly the INVALID_USERNAME_PASSWORD shape asserted below.
 */
class GarminConnectAuthClientTest {

    private final GarminConnectAuthClient client = new GarminConnectAuthClient();

    @Test
    void successfulLogin_returnsServiceTicket() {
        String body = """
                {"serviceTicketId":"ST-12345-abc","responseStatus":{"type":"SUCCESSFUL"}}
                """;

        SsoParseResult result = client.parseSsoResponse(200, body);

        assertThat(result).isInstanceOf(SsoParseResult.Successful.class);
        assertThat(((SsoParseResult.Successful) result).serviceTicketId()).isEqualTo("ST-12345-abc");
    }

    @Test
    void mfaRequired_extractsMethodFromCustomerMfaInfo() {
        String body = """
                {"responseStatus":{"type":"MFA_REQUIRED"},"customerMfaInfo":{"mfaLastMethodUsed":"sms"}}
                """;

        SsoParseResult result = client.parseSsoResponse(200, body);

        assertThat(result).isInstanceOf(SsoParseResult.MfaRequired.class);
        assertThat(((SsoParseResult.MfaRequired) result).mfaMethod()).isEqualTo("sms");
    }

    @Test
    void mfaRequired_defaultsMethodToEmailWhenMissing() {
        String body = """
                {"responseStatus":{"type":"MFA_REQUIRED"}}
                """;

        SsoParseResult result = client.parseSsoResponse(200, body);

        assertThat(((SsoParseResult.MfaRequired) result).mfaMethod()).isEqualTo("email");
    }

    @Test
    void invalidCredentials_isFlaggedAndStopsAnyRetryChain() {
        String body = """
                {"responseStatus":{"type":"INVALID_USERNAME_PASSWORD"}}
                """;

        SsoParseResult result = client.parseSsoResponse(200, body);

        assertThat(result).isInstanceOf(SsoParseResult.Failed.class);
        SsoParseResult.Failed failed = (SsoParseResult.Failed) result;
        assertThat(failed.invalidCredentials()).isTrue();
        assertThat(failed.rateLimited()).isFalse();
    }

    @Test
    void captchaRequired_isFailedButNotInvalidCredentials() {
        String body = """
                {"responseStatus":{"type":"CAPTCHA_REQUIRED"}}
                """;

        SsoParseResult result = client.parseSsoResponse(200, body);

        SsoParseResult.Failed failed = (SsoParseResult.Failed) result;
        assertThat(failed.invalidCredentials()).isFalse();
        assertThat(failed.rateLimited()).isFalse();
    }

    @Test
    void httpTooManyRequests_isFlaggedRateLimited() {
        SsoParseResult result = client.parseSsoResponse(429, "");

        SsoParseResult.Failed failed = (SsoParseResult.Failed) result;
        assertThat(failed.rateLimited()).isTrue();
    }

    @Test
    void rateLimitBuriedInJsonBody_isAlsoFlaggedRateLimited() {
        String body = """
                {"responseStatus":{"type":"UNKNOWN"},"error":{"status-code":"429"}}
                """;

        SsoParseResult result = client.parseSsoResponse(200, body);

        SsoParseResult.Failed failed = (SsoParseResult.Failed) result;
        assertThat(failed.rateLimited()).isTrue();
    }

    @Test
    void httpForbidden_isTreatedAsBotChallengeNotInvalidCredentials() {
        SsoParseResult result = client.parseSsoResponse(403, "");

        SsoParseResult.Failed failed = (SsoParseResult.Failed) result;
        assertThat(failed.invalidCredentials()).isFalse();
        assertThat(failed.message()).contains("403");
    }

    @Test
    void nonJsonBody_failsCleanlyInsteadOfThrowing() {
        SsoParseResult result = client.parseSsoResponse(502, "<html>Bad Gateway</html>");

        assertThat(result).isInstanceOf(SsoParseResult.Failed.class);
    }

    @Test
    void successfulLogin_missingServiceTicket_failsCleanly() {
        String body = """
                {"responseStatus":{"type":"SUCCESSFUL"}}
                """;

        SsoParseResult result = client.parseSsoResponse(200, body);

        assertThat(result).isInstanceOf(SsoParseResult.Failed.class);
    }
}
