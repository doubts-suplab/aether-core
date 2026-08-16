package com.suplab.aether.core.api.security;

import com.suplab.aether.core.domain.SubjectVerificationToken;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSubjectVerifierTest {

    private static final String SECRET = "verifier-secret";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void whenDisabled_everyRequestIsAuthorised() {
        var verifier = new DataSubjectVerifier(false, "", FIXED);
        assertThat(verifier.required()).isFalse();
        assertThat(verifier.isVerified("u-1", null)).isTrue();
        assertThat(verifier.isVerified("u-1", "garbage")).isTrue();
    }

    @Test
    void whenEnabledWithNoSecret_constructionFailsClosed() {
        assertThatThrownBy(() -> new DataSubjectVerifier(true, "  ", FIXED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("verification-secret");
    }

    @Test
    void whenEnabled_onlyValidTokenForTheUserIsAuthorised() {
        var verifier = new DataSubjectVerifier(true, SECRET, FIXED);
        assertThat(verifier.required()).isTrue();

        assertThat(verifier.isVerified("u-1", null)).isFalse();
        assertThat(verifier.isVerified("u-1", "bad")).isFalse();

        var valid = SubjectVerificationToken.mint("u-1", SECRET, NOW.plusSeconds(300));
        assertThat(verifier.isVerified("u-1", valid)).isTrue();
        // Same token, different subject → rejected.
        assertThat(verifier.isVerified("u-2", valid)).isFalse();
    }

    @Test
    void whenEnabled_expiryIsCheckedAgainstTheInjectedClock() {
        var verifier = new DataSubjectVerifier(true, SECRET, FIXED);
        var expired = SubjectVerificationToken.mint("u-1", SECRET, NOW.minusSeconds(1));
        assertThat(verifier.isVerified("u-1", expired)).isFalse();
    }
}
