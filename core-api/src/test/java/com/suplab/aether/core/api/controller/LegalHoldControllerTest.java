package com.suplab.aether.core.api.controller;

import com.suplab.aether.core.domain.DataCategory;
import com.suplab.aether.core.domain.LegalHold;
import com.suplab.aether.core.ports.LegalHoldStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LegalHoldControllerTest {

    private static final class FakeHoldStore implements LegalHoldStore {
        final List<LegalHold> placed = new ArrayList<>();
        final List<DataCategory> lifted = new ArrayList<>();
        int liftReturn = 1;
        @Override public void place(LegalHold hold) { placed.add(hold); }
        @Override public int lift(String userId, DataCategory category) { lifted.add(category); return liftReturn; }
        @Override public Set<DataCategory> heldCategories(String userId) {
            return placed.isEmpty() ? Set.of()
                    : EnumSet.copyOf(placed.stream().map(LegalHold::category).toList());
        }
        @Override public List<LegalHold> findByUser(String userId) { return List.copyOf(placed); }
    }

    @Test
    void place_storesHoldAndReturnsView() {
        var store = new FakeHoldStore();
        var controller = new LegalHoldController(store);

        var res = controller.place("u-1", DataCategory.SESSIONS,
                new LegalHoldController.HoldRequest("active litigation", "legal@acme"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(store.placed).hasSize(1);
        assertThat(store.placed.getFirst().category()).isEqualTo(DataCategory.SESSIONS);
        assertThat(store.placed.getFirst().placedBy()).isEqualTo("legal@acme");
        assertThat(res.getBody().get("category")).isEqualTo("SESSIONS");
        assertThat(res.getBody().get("reason")).isEqualTo("active litigation");
    }

    @Test
    void place_defaultsPlacedByWhenBlank() {
        var store = new FakeHoldStore();
        new LegalHoldController(store).place("u-1", DataCategory.MEMORIES,
                new LegalHoldController.HoldRequest("regulatory retention", " "));

        assertThat(store.placed.getFirst().placedBy()).isEqualTo("compliance");
    }

    @Test
    void lift_removesHoldAndReturnsCount() {
        var store = new FakeHoldStore();
        var res = new LegalHoldController(store).lift("u-1", DataCategory.PREFERENCES);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(store.lifted).containsExactly(DataCategory.PREFERENCES);
        assertThat(res.getBody().get("removed")).isEqualTo(1);
    }

    @Test
    void list_returnsHoldViews() {
        var store = new FakeHoldStore();
        store.place(LegalHold.of("u-1", DataCategory.MEMORIES, "r", "legal@acme"));
        var res = new LegalHoldController(store).list("u-1");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).hasSize(1);
        assertThat(res.getBody().getFirst().get("category")).isEqualTo("MEMORIES");
    }
}
