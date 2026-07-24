package com.suplab.aether.core.api.controller;

import com.suplab.aether.core.domain.ErasureEvent;
import com.suplab.aether.core.domain.ErasureScope;
import com.suplab.aether.core.ports.ErasureEventStore;
import com.suplab.aether.core.ports.PersonalDataErasurePort;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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

    private DataSubjectController controller(FakeErasurePort port) {
        return new DataSubjectController(port, new FakeEventStore());
    }

    @Test
    void eraseMemories_returns200WithView() {
        var port = new FakeErasurePort();
        var res = controller(port).eraseMemories("u-1", "ops@acme");

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
        var res = controller(port).eraseAccount("u-1", null);

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
}
