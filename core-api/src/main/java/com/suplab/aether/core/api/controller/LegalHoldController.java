package com.suplab.aether.core.api.controller;

import com.suplab.aether.core.domain.DataCategory;
import com.suplab.aether.core.domain.LegalHold;
import com.suplab.aether.core.ports.LegalHoldStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Legal / statutory retention holds over a user's personal data.
 *
 * <p>A hold marks a {@link DataCategory} that a right-to-erasure request must retain rather than
 * delete — the mechanism by which Core honours retention exceptions across jurisdictions (GDPR
 * Art. 17(3), CCPA §1798.105(d), HIPAA/GLBA mandates, active litigation). Holds are an operator /
 * compliance concern, distinct from the data subject's own erasure rights, so they live on their own
 * path. Placing or lifting a hold changes what a subsequent erasure will delete.</p>
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/legal-holds")
public class LegalHoldController {

    private static final Logger log = LoggerFactory.getLogger(LegalHoldController.class);

    private final LegalHoldStore legalHoldStore;

    public LegalHoldController(LegalHoldStore legalHoldStore) {
        this.legalHoldStore = legalHoldStore;
    }

    /**
     * Lists the holds currently in place for a user, most recent first.
     *
     * @return 200 OK with the list of hold views
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(@PathVariable String userId) {
        var body = legalHoldStore.findByUser(userId).stream().map(LegalHoldController::toView).toList();
        return ResponseEntity.ok(body);
    }

    /**
     * Places (or refreshes) a hold on a category for a user. Idempotent per category.
     *
     * @param userId   the data subject
     * @param category the category to retain
     * @param request  the legal basis and who placed it
     * @return 200 OK with the placed hold view
     */
    @PutMapping("/{category}")
    public ResponseEntity<Map<String, Object>> place(@PathVariable String userId,
                                                     @PathVariable DataCategory category,
                                                     @RequestBody HoldRequest request) {
        var hold = LegalHold.of(userId, category, request.reason(),
                request.placedBy() == null || request.placedBy().isBlank() ? "compliance" : request.placedBy());
        legalHoldStore.place(hold);
        log.info("Placed legal hold userId={} category={}", userId, category);
        return ResponseEntity.ok(toView(hold));
    }

    /**
     * Lifts a hold, allowing the category to be erased again.
     *
     * @return 200 OK with {@code {removed: n}}
     */
    @DeleteMapping("/{category}")
    public ResponseEntity<Map<String, Object>> lift(@PathVariable String userId,
                                                    @PathVariable DataCategory category) {
        int removed = legalHoldStore.lift(userId, category);
        log.info("Lifted legal hold userId={} category={} removed={}", userId, category, removed);
        return ResponseEntity.ok(Map.of("userId", userId, "category", category.name(), "removed", removed));
    }

    private static Map<String, Object> toView(LegalHold hold) {
        return Map.of(
                "userId", hold.userId(),
                "category", hold.category().name(),
                "reason", hold.reason(),
                "placedBy", hold.placedBy(),
                "placedAt", hold.placedAt().toString());
    }

    /**
     * Request body for placing a hold.
     *
     * @param reason   the legal basis for retention (required)
     * @param placedBy who placed the hold (defaulted to {@code compliance} when blank)
     */
    public record HoldRequest(String reason, String placedBy) {
    }
}
