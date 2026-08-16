package com.suplab.aether.core.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectVerificationTokenTest {

    private static final String SECRET = "shared-signing-secret";

    @Test
    void mintedToken_verifiesForTheSameUser() {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var token = SubjectVerificationToken.mint("user-1", SECRET, now.plusSeconds(300));
        assertThat(SubjectVerificationToken.verify("user-1", token, SECRET, now)).isTrue();
    }

    @Test
    void tokenForOneUser_doesNotVerifyForAnother() {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var token = SubjectVerificationToken.mint("user-1", SECRET, now.plusSeconds(300));
        assertThat(SubjectVerificationToken.verify("user-2", token, SECRET, now)).isFalse();
    }

    @Test
    void expiredToken_isRejected() {
        var issuedAt = Instant.parse("2026-01-01T00:00:00Z");
        var token = SubjectVerificationToken.mint("user-1", SECRET, issuedAt.plusSeconds(60));
        // One second past expiry.
        assertThat(SubjectVerificationToken.verify("user-1", token, SECRET, issuedAt.plusSeconds(61)))
                .isFalse();
        // Exactly at expiry is still valid.
        assertThat(SubjectVerificationToken.verify("user-1", token, SECRET, issuedAt.plusSeconds(60)))
                .isTrue();
    }

    @Test
    void wrongSecret_isRejected() {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var token = SubjectVerificationToken.mint("user-1", SECRET, now.plusSeconds(300));
        assertThat(SubjectVerificationToken.verify("user-1", token, "other-secret", now)).isFalse();
    }

    @Test
    void tamperedSignature_isRejected() {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var token = SubjectVerificationToken.mint("user-1", SECRET, now.plusSeconds(300));
        var tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "B" : "A");
        assertThat(SubjectVerificationToken.verify("user-1", tampered, SECRET, now)).isFalse();
    }

    @Test
    void malformedTokens_areRejectedNotThrown() {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        assertThat(SubjectVerificationToken.verify("user-1", null, SECRET, now)).isFalse();
        assertThat(SubjectVerificationToken.verify("user-1", "", SECRET, now)).isFalse();
        assertThat(SubjectVerificationToken.verify("user-1", "no-dot", SECRET, now)).isFalse();
        assertThat(SubjectVerificationToken.verify("user-1", "notanumber.sig", SECRET, now)).isFalse();
        assertThat(SubjectVerificationToken.verify("user-1", "123.", SECRET, now)).isFalse();
    }

    @Test
    void mint_rejectsBlankInputs() {
        var future = Instant.now().plusSeconds(60);
        assertThatThrownBy(() -> SubjectVerificationToken.mint(" ", SECRET, future))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SubjectVerificationToken.mint("u", " ", future))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
