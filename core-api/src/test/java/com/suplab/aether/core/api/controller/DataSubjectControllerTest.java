package com.suplab.aether.core.api.controller;

import com.suplab.aether.core.api.security.DataSubjectVerifier;
import com.suplab.aether.core.domain.CognitiveSession;
import com.suplab.aether.core.domain.ErasureEvent;
import com.suplab.aether.core.domain.ErasureScope;
import com.suplab.aether.core.domain.MemoryType;
import com.suplab.aether.core.domain.PersonalDataExport;
import com.suplab.aether.core.domain.PersonalMemory;
import com.suplab.aether.core.domain.SubjectVerificationToken;
import com.suplab.aether.core.ports.ErasureEventStore;
import com.suplab.aether.core.ports.PersonalDataErasurePort;
import com.suplab.aether.core.ports.PersonalDataExportPort;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataSubjectControllerTest {

    private static final class FakeErasurePort implements PersonalDataErasurePort {
        String lastMethod;
        String lastRequestedBy;
        @Override public ErasureEvent eraseMemories(String userId, String requestedBy) {
            lastMethod = "memories"; lastRequestedBy = requestedBy;
            return ErasureEvent.of(userId, ErasureScope.MEMORIES, 4, 0, 0, requestedBy);
        }
        @Override public ErasureEvent eraseAccount(String userId, String requestedBy) {
            lastMethod = "account"; lastRequestedBy = requestedBy;
            return ErasureEvent.of(userId, ErasureScope.ACCOUNT, 4, 2, 1, requestedBy);
        }
    }

    private static final class FakeEventStore implements ErasureEventStore {
        @Override public void record(ErasureEvent event) { }
        @Override public List<ErasureEvent> findByUser(String userId, int limit) {
            return List.of(ErasureEvent.of(userId, ErasureScope.ACCOUNT, 1, 1, 1, "self"));
        }
    }

    private static final class FakeExportPort implements PersonalDataExportPort {
        String lastUserId;
        @Override public PersonalDataExport exportAll(String userId) {
            lastUserId = userId;
            var memory = PersonalMemory.create(userId, MemoryType.SEMANTIC, "the sky is blue");
            var session = CognitiveSession.start("t-1", userId);
            return PersonalDataExport.of(userId, List.of(memory), List.of(session),
                    Map.of("theme", "dark"));
        }
    }

    private static final String SECRET = "test-verification-secret";

    /** Verification disabled — the standalone default. */
    private static final DataSubjectVerifier OPEN =
            new DataSubjectVerifier(false, "", Clock.systemUTC());

    /** Verification required — sensitive requests need a valid X-Subject-Verification token. */
    private static final DataSubjectVerifier REQUIRED =
            new DataSubjectVerifier(true, SECRET, Clock.systemUTC());

    private DataSubjectController controller(FakeErasurePort port) {
        return new DataSubjectController(port, new FakeEventStore(), new FakeExportPort(), OPEN);
    }

    private DataSubjectController requiring(FakeErasurePort port) {
        return new DataSubjectController(port, new FakeEventStore(), new FakeExportPort(), REQUIRED);
    }

    private static String validToken(String userId) {
        return SubjectVerificationToken.mint(userId, SECRET, Instant.now().plusSeconds(300));
    }

    @Test
    void eraseMemories_returns200WithView() {
        var port = new FakeErasurePort();
        var res = controller(port).eraseMemories("u-1", "ops@acme", null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(port.lastMethod).isEqualTo("memories");
        assertThat(port.lastRequestedBy).isEqualTo("ops@acme");
        var body = res.getBody();
        assertThat(body.get("scope")).isEqualTo("MEMORIES");
        assertThat(body.get("memoriesErased")).isEqualTo(4);
        assertThat(body.get("totalErased")).isEqualTo(4);
    }

    @Test
    void eraseAccount_returns200AndErasesEverything() {
        var port = new FakeErasurePort();
        var res = controller(port).eraseAccount("u-1", null, null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(port.lastMethod).isEqualTo("account");
        var body = res.getBody();
        assertThat(body.get("scope")).isEqualTo("ACCOUNT");
        assertThat(body.get("totalErased")).isEqualTo(7); // 4 + 2 + 1
    }

    @Test
    void erasureHistory_returns200WithList() {
        var res = controller(new FakeErasurePort()).erasureHistory("u-1", 20);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) res.getBody()).hasSize(1);
    }

    @Test
    void export_returns200WithPortableView() {
        var res = controller(new FakeErasurePort()).export("u-1", null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = res.getBody();
        assertThat(body.get("userId")).isEqualTo("u-1");
        assertThat(body).containsKey("exportedAt");
        assertThat(body.get("totalRecords")).isEqualTo(3); // 1 memory + 1 session + 1 preference key
        assertThat((List<?>) body.get("memories")).hasSize(1);
        assertThat((List<?>) body.get("sessions")).hasSize(1);
        @SuppressWarnings("unchecked")
        var preferences = (Map<String, Object>) body.get("preferences");
        assertThat(preferences).containsEntry("theme", "dark");
    }

    @Test
    void whenVerificationRequired_missingOrBadToken_is401_andDataUntouched() {
        var port = new FakeErasurePort();
        var missing = requiring(port).eraseAccount("u-1", null, null);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        var wrong = requiring(port).eraseAccount("u-1", null, "not-a-real-token");
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // The erasure port was never invoked — a rejected request touches no data.
        assertThat(port.lastMethod).isNull();
    }

    @Test
    void whenVerificationRequired_tokenForAnotherUser_is401() {
        var res = requiring(new FakeErasurePort())
                .eraseMemories("u-1", null, validToken("someone-else"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void whenVerificationRequired_validToken_authorisesErasureAndExport() {
        var port = new FakeErasurePort();
        var erase = requiring(port).eraseMemories("u-1", "self", validToken("u-1"));
        assertThat(erase.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(port.lastMethod).isEqualTo("memories");

        var export = requiring(new FakeErasurePort()).export("u-1", validToken("u-1"));
        assertThat(export.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(export.getBody().get("userId")).isEqualTo("u-1");
    }

    @Test
    void erasureHistory_isNeverGated() {
        // Read-only audit metadata stays reachable even with verification required and no token.
        var res = requiring(new FakeErasurePort()).erasureHistory("u-1", 20);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) res.getBody()).hasSize(1);
    }
}
