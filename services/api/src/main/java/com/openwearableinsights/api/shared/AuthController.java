package com.openwearableinsights.api.shared;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Local API token rotation (ADR-0008) — the counterpart to pairing: a way to
 * invalidate the current token (e.g. after a screen share, screenshot, or
 * anywhere else it might have been exposed) without editing files by hand.
 *
 * <p>This endpoint is itself behind {@link LocalApiTokenAuthFilter} like
 * every other {@code /api/v1/**} route — only a client that already holds
 * the current valid token can call it. That's what makes this safe to
 * self-serve: the token is generated, persisted, and returned in the same
 * response, so the calling client rotates itself atomically and is never
 * locked out of its own action. Any *other* client still holding the old
 * token starts getting 401s and has to re-pair — same as if the token file
 * had been edited by hand.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Local API token rotation")
public class AuthController {

    private final LocalApiTokenStore tokenStore;

    public AuthController(LocalApiTokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @PostMapping("/regenerate-token")
    @Operation(summary = "Regenerate the local API token",
            description = "Generates a new token, persists it, and returns it. The caller must immediately " +
                    "store the new token — the one used to authenticate this request stops working right away. " +
                    "Any other paired client/tab is logged out and must re-pair with the new token.")
    public RegenerateTokenResponse regenerateToken() {
        try {
            return new RegenerateTokenResponse(tokenStore.regenerate());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist regenerated local API token", e);
        }
    }

    public record RegenerateTokenResponse(String token) {}
}
