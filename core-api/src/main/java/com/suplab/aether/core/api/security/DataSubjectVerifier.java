package com.suplab.aether.core.api.security;

import com.suplab.aether.core.domain.SubjectVerificationToken;

import java.time.Clock;

/**
 * Config-gated gate that requires a verified-identity token before a sensitive data-subject request
 * (erasure, export) is honoured (GDPR Art. 12(6)).
 *
 * <p><strong>Off by default</strong> so Core still runs open standalone: when
 * {@code aether.core.data-subject.require-verification=false} every request is authorised. When
 * enabled, a request must carry a valid {@link SubjectVerificationToken} bound to the target
 * {@code userId}. The gate is <strong>fail-closed</strong>: requiring verification with no signing
 * secret configured fails construction rather than silently allowing everything.</p>
 *
 * <p>Verification is constant-time and expiry-aware (delegated to {@link SubjectVerificationToken}).
 * The injected {@link Clock} keeps expiry checks testable.</p>
 */
public class DataSubjectVerifier {

    private final boolean requireVerification;
    private final String secret;
    private final Clock clock;

    public DataSubjectVerifier(boolean requireVerification, String secret, Clock clock) {
        if (requireVerification && (secret == null || secret.isBlank())) {
            throw new IllegalStateException(
                    "aether.core.data-subject.require-verification=true requires a non-blank "
                            + "aether.core.data-subject.verification-secret (fail-closed)");
        }
        this.requireVerification = requireVerification;
        this.secret = secret;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /** @return {@code true} if a verification token is required for sensitive data-subject requests. */
    public boolean required() {
        return requireVerification;
    }

    /**
     * @param userId the data subject the request targets
     * @param token  the presented {@code X-Subject-Verification} token (may be {@code null})
     * @return {@code true} if the request is authorised — always when verification is disabled, else
     *         only when the token is a valid, unexpired proof bound to {@code userId}
     */
    public boolean isVerified(String userId, String token) {
        if (!requireVerification) return true;
        return SubjectVerificationToken.verify(userId, token, secret, clock.instant());
    }
}
