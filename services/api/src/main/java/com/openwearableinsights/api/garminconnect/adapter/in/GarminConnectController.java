package com.openwearableinsights.api.garminconnect.adapter.in;

import com.openwearableinsights.api.garminconnect.application.GarminConnectService;
import com.openwearableinsights.api.garminconnect.application.GarminConnectService.ConnectResult;
import com.openwearableinsights.api.garminconnect.application.GarminConnectSyncService;
import com.openwearableinsights.api.garminconnect.domain.GarminConnectAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * REST controller for the Garmin Connect connector — real login (with MFA
 * support), session status, disconnect, and historical sync. The user's
 * password is only ever received here, forwarded once to Garmin's own
 * servers to authenticate, and never stored — see GarminConnectAuthClient.
 */
@RestController
@RequestMapping("/api/v1/garmin-connect")
@Tag(name = "Garmin Connect", description = "Real Garmin Connect account login (unofficial mobile-app API) for historical data backfill")
public class GarminConnectController {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    private final GarminConnectService connectService;
    private final GarminConnectSyncService syncService;

    public GarminConnectController(GarminConnectService connectService, GarminConnectSyncService syncService) {
        this.connectService = connectService;
        this.syncService = syncService;
    }

    @GetMapping("/status")
    @Operation(summary = "Get the current Garmin Connect connection status")
    public StatusResponse status() {
        GarminConnectAccount account = connectService.status();
        return new StatusResponse(account.status(), account.email(), account.lastError(),
                account.connectedAt(), account.lastSyncAt());
    }

    @PostMapping("/connect")
    @Operation(summary = "Log in to Garmin Connect with the account's real credentials")
    public ConnectResponse connect(@RequestBody ConnectRequest request) {
        return toResponse(connectService.connect(request.email(), request.password()));
    }

    @PostMapping("/mfa")
    @Operation(summary = "Submit the MFA code Garmin sent, to complete an in-progress login")
    public ConnectResponse submitMfa(@RequestBody MfaRequest request) {
        return toResponse(connectService.submitMfaCode(request.code()));
    }

    @DeleteMapping
    @Operation(summary = "Disconnect Garmin Connect and discard stored tokens")
    public void disconnect() {
        connectService.disconnect();
    }

    @PostMapping("/sync")
    @Operation(summary = "Fetch real historical sleep/HRV/stress/RHR/steps data for a date range")
    public GarminConnectSyncService.SyncResult sync(@RequestBody SyncRequest request) {
        return syncService.sync(DEFAULT_ACCOUNT_ID, request.startDate(), request.endDate());
    }

    private ConnectResponse toResponse(ConnectResult result) {
        return switch (result) {
            case ConnectResult.Connected c -> new ConnectResponse("connected", c.email(), null, null);
            case ConnectResult.MfaRequired m -> new ConnectResponse("mfa_required", null, m.method(), null);
            case ConnectResult.Failed f -> new ConnectResponse("error", null, null, f.message());
        };
    }

    public record ConnectRequest(String email, String password) {}

    public record MfaRequest(String code) {}

    public record SyncRequest(LocalDate startDate, LocalDate endDate) {}

    public record ConnectResponse(String status, String email, String mfaMethod, String message) {}

    public record StatusResponse(
            String status, String email, String lastError,
            java.time.Instant connectedAt, java.time.Instant lastSyncAt
    ) {}
}
