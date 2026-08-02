package com.openwearableinsights.api.garminconnect.application;

import com.openwearableinsights.api.garminconnect.application.GarminConnectAuthClient.LoginOutcome;
import com.openwearableinsights.api.garminconnect.application.GarminConnectAuthClient.PendingMfa;
import com.openwearableinsights.api.garminconnect.domain.GarminConnectAccount;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates the Garmin Connect connector: login, MFA completion, status,
 * and disconnect. Owns the one piece of state that can't live in the
 * database — an in-progress MFA challenge's session cookies — kept in
 * memory only, keyed by account id, and discarded as soon as it's resolved
 * (or the server restarts, in which case the user just logs in again).
 */
@Service
public class GarminConnectService {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    private final GarminConnectAuthClient authClient;
    private final GarminConnectAccountRepository accountRepository;
    private final Map<Long, PendingMfa> pendingMfaByAccount = new ConcurrentHashMap<>();

    public GarminConnectService(GarminConnectAuthClient authClient, GarminConnectAccountRepository accountRepository) {
        this.authClient = authClient;
        this.accountRepository = accountRepository;
    }

    public sealed interface ConnectResult {
        record Connected(String email) implements ConnectResult {}
        record MfaRequired(String method) implements ConnectResult {}
        record Failed(String message) implements ConnectResult {}
    }

    public ConnectResult connect(String email, String password) {
        LoginOutcome outcome = authClient.login(email, password);
        return applyOutcome(email, outcome);
    }

    public ConnectResult submitMfaCode(String code) {
        PendingMfa pending = pendingMfaByAccount.get(DEFAULT_ACCOUNT_ID);
        if (pending == null) {
            return new ConnectResult.Failed("No Garmin Connect login is waiting for an MFA code — start over.");
        }
        LoginOutcome outcome = authClient.submitMfa(pending, code);
        return applyOutcome(pending.email(), outcome);
    }

    private ConnectResult applyOutcome(String email, LoginOutcome outcome) {
        return switch (outcome) {
            case LoginOutcome.Authenticated a -> {
                pendingMfaByAccount.remove(DEFAULT_ACCOUNT_ID);
                accountRepository.saveConnected(DEFAULT_ACCOUNT_ID, email, a.tokens());
                yield new ConnectResult.Connected(email);
            }
            case LoginOutcome.MfaRequired m -> {
                pendingMfaByAccount.put(DEFAULT_ACCOUNT_ID, m.pending());
                accountRepository.saveMfaRequired(DEFAULT_ACCOUNT_ID, email);
                yield new ConnectResult.MfaRequired(m.pending().mfaMethod());
            }
            case LoginOutcome.Failed f -> {
                pendingMfaByAccount.remove(DEFAULT_ACCOUNT_ID);
                accountRepository.saveError(DEFAULT_ACCOUNT_ID, f.message());
                yield new ConnectResult.Failed(f.message());
            }
        };
    }

    public GarminConnectAccount status() {
        return accountRepository.find(DEFAULT_ACCOUNT_ID).orElse(GarminConnectAccount.disconnected(DEFAULT_ACCOUNT_ID));
    }

    public void disconnect() {
        pendingMfaByAccount.remove(DEFAULT_ACCOUNT_ID);
        accountRepository.disconnect(DEFAULT_ACCOUNT_ID);
    }
}
