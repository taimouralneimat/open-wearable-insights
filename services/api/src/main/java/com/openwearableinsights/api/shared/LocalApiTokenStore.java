package com.openwearableinsights.api.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the single, persistent, per-machine local API token used to authenticate
 * requests to {@code /api/v1/**} (see ADR-0008: single-user local auth with an
 * OIDC seam for later hosted deployment).
 *
 * <p>On first startup, generates a random token and writes it to
 * {@code owi.local-api-token-file} (env: {@code OWI_LOCAL_API_TOKEN_FILE}),
 * defaulting to {@code ~/.open-wearable-insights/local-api-token}, with
 * owner-only read/write permissions. On subsequent startups, the existing
 * token is read and reused so restarting the server does not invalidate
 * clients that already have the token.
 *
 * <p>The token value is never logged; only the file path is logged, since the
 * client (or the user, via {@code cat}) is expected to read the token
 * directly from that file.
 */
@Component
public class LocalApiTokenStore {

    private static final Logger log = LoggerFactory.getLogger(LocalApiTokenStore.class);
    private static final int TOKEN_BYTES = 32;

    private final Path tokenFile;
    private final AtomicReference<String> token;

    public LocalApiTokenStore(
            @Value("${owi.local-api-token-file:~/.open-wearable-insights/local-api-token}") String tokenFilePath) {
        this.tokenFile = resolvePath(tokenFilePath);
        this.token = new AtomicReference<>(loadOrGenerateToken(this.tokenFile));
    }

    /**
     * The current local API token. Never null after construction.
     */
    public String token() {
        return token.get();
    }

    /**
     * Generates a fresh token, persists it (overwriting the old one), and
     * returns it. Callable only by a client that already holds the current
     * valid token — this method has no auth of its own, it relies entirely
     * on {@link LocalApiTokenAuthFilter} already having verified the caller
     * against the token being replaced, same as every other {@code
     * /api/v1/**} endpoint. The caller is expected to immediately store the
     * returned value as its new token — see {@code POST
     * /api/v1/auth/regenerate-token} — so a single already-authenticated
     * client rotates itself atomically, with no separate re-pairing step and
     * no window where it's locked out of its own action.
     */
    public synchronized String regenerate() throws IOException {
        String fresh = generateToken();
        writeTokenFile(tokenFile, fresh);
        token.set(fresh);
        log.info("Local API token regenerated at {}. Any other client still using the previous " +
                "token will get 401s and need to re-pair.", tokenFile);
        return fresh;
    }

    /**
     * Constant-time-ish comparison is not critical for a local single-user
     * loopback token (no network attacker in the threat model per ADR-0008),
     * but String#equals is fine here — this is a local file-secret check, not
     * a remote cryptographic comparison.
     */
    public boolean matches(String candidate) {
        return candidate != null && token.get().equals(candidate);
    }

    private static String loadOrGenerateToken(Path tokenFile) {
        try {
            if (Files.exists(tokenFile)) {
                String existing = Files.readString(tokenFile, StandardCharsets.UTF_8).strip();
                if (!existing.isEmpty()) {
                    log.info("Local API token loaded from existing file at {}. "
                            + "Clients must send it in the 'X-Local-Api-Token' header for /api/v1/** requests.",
                            tokenFile);
                    return existing;
                }
                log.warn("Local API token file at {} exists but is empty; generating a new token.", tokenFile);
            }

            String generated = generateToken();
            writeTokenFile(tokenFile, generated);
            log.info("Generated a new local API token and wrote it to {}. "
                    + "Clients must send it in the 'X-Local-Api-Token' header for /api/v1/** requests. "
                    + "Read the token value with: cat {}", tokenFile, tokenFile);
            return generated;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to initialize local API token at " + tokenFile
                            + " — refusing to start unprotected. See ADR-0008.", e);
        }
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Creates the file with owner-only permissions if it doesn't exist yet; overwrites its content either way — used both for first-boot generation and later regeneration. */
    private static void writeTokenFile(Path tokenFile, String token) throws IOException {
        Path parent = tokenFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        boolean posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        if (posix) {
            if (!Files.exists(tokenFile)) {
                FileAttribute<?> ownerOnly = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
                Files.createFile(tokenFile, ownerOnly);
            }
            Files.writeString(tokenFile, token, StandardCharsets.UTF_8);
        } else {
            Files.writeString(tokenFile, token, StandardCharsets.UTF_8);
            java.io.File f = tokenFile.toFile();
            // Lock down to owner-only read/write on filesystems without POSIX permission support.
            f.setReadable(false, false);
            f.setWritable(false, false);
            f.setReadable(true, true);
            f.setWritable(true, true);
        }
    }

    private static Path resolvePath(String raw) {
        String expanded = raw;
        if (raw.startsWith("~")) {
            String home = System.getProperty("user.home");
            expanded = raw.replaceFirst("^~", home);
        }
        return Paths.get(expanded);
    }
}
