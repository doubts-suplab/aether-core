package com.suplab.aether.core.domain;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * A signed, time-boxed proof that a data-subject request was made by a verified identity.
 *
 * <p>GDPR Article 12(6) lets a controller demand additional information to confirm the identity of a
 * data subject before acting on a request. Aether Core does not itself run the identity check (no email
 * or OTP channel lives in this bounded context); instead a trusted front door that <em>has</em> verified
 * the subject mints one of these tokens with a shared secret, and Core verifies it before honouring a
 * sensitive request (erasure, export). This mirrors the ecosystem's other shared-secret seams (e.g.
 * Memory's federation bearer token): Core verifies, it does not issue to the public.</p>
 *
 * <p>The token binds a {@code userId} and an expiry: {@code <expiryEpochSeconds>.<base64url(HMAC)>},
 * where the HMAC is {@code HMAC-SHA256(secret, userId + ":" + expiryEpochSeconds)}. Verification
 * recomputes the HMAC, compares it in constant time, and rejects an expired or tampered token. A token
 * minted for one user never validates for another.</p>
 */
public final class SubjectVerificationToken {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private SubjectVerificationToken() {
    }

    /**
     * Mints a token binding {@code userId}, valid until {@code expiresAt}.
     *
     * @param userId    the data subject the token authorises
     * @param secret    the shared signing secret (must be non-blank)
     * @param expiresAt the instant the token stops being valid
     * @return the encoded token
     */
    public static String mint(String userId, String secret, Instant expiresAt) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId required");
        if (secret == null || secret.isBlank()) throw new IllegalArgumentException("secret required");
        if (expiresAt == null) throw new IllegalArgumentException("expiresAt required");
        long expiry = expiresAt.getEpochSecond();
        String signature = URL_ENCODER.encodeToString(sign(userId, expiry, secret));
        return expiry + "." + signature;
    }

    /**
     * Verifies that {@code token} is a valid, unexpired proof for {@code userId}.
     *
     * @param userId the data subject the request targets
     * @param token  the presented token (may be {@code null}/blank → invalid)
     * @param secret the shared signing secret
     * @param now    the current instant (expiry is checked against this)
     * @return {@code true} only if the token is well-formed, bound to {@code userId}, correctly
     *         signed, and not yet expired
     */
    public static boolean verify(String userId, String token, String secret, Instant now) {
        if (userId == null || userId.isBlank() || token == null || token.isBlank()) return false;
        if (secret == null || secret.isBlank() || now == null) return false;
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) return false;
        long expiry;
        try {
            expiry = Long.parseLong(token.substring(0, dot));
        } catch (NumberFormatException e) {
            return false;
        }
        if (now.getEpochSecond() > expiry) return false; // expired
        byte[] presented;
        try {
            presented = URL_DECODER.decode(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            return false;
        }
        byte[] expected = sign(userId, expiry, secret);
        return MessageDigest.isEqual(expected, presented);
    }

    private static byte[] sign(String userId, long expiry, String secret) {
        try {
            var mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal((userId + ":" + expiry).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            // HmacSHA256 is a required JDK algorithm; a failure here is non-recoverable.
            throw new IllegalStateException("HMAC signing failed", e);
        }
    }
}
