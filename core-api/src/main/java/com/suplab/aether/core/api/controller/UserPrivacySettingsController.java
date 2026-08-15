package com.suplab.aether.core.api.controller;

import com.suplab.aether.core.domain.UserPrivacySettings;
import com.suplab.aether.core.ports.UserPrivacySettingsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Per-user privacy settings — the retention window that drives the automated retention purge
 * (GDPR storage-limitation, Art. 5(1)(e)).
 *
 * <p>Every path is scoped by {@code userId}. {@code dataRetentionDays} of {@code 0} means "keep
 * indefinitely" — the user's data is never automatically purged, only erased on explicit request.
 * A positive value opts the user into the scheduled retention sweep.</p>
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/privacy-settings")
public class UserPrivacySettingsController {

    private static final Logger log = LoggerFactory.getLogger(UserPrivacySettingsController.class);

    private final UserPrivacySettingsStore store;

    public UserPrivacySettingsController(UserPrivacySettingsStore store) {
        this.store = store;
    }

    /** Request body for setting the retention window. */
    public record PrivacySettingsRequest(int dataRetentionDays) {}

    /**
     * Returns the user's privacy settings, defaulting to "keep indefinitely" (0) when none are set.
     *
     * @return 200 OK with the settings view
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> get(@PathVariable String userId) {
        var settings = store.find(userId).orElseGet(() -> UserPrivacySettings.of(userId, 0));
        return ResponseEntity.ok(toView(settings));
    }

    /**
     * Sets the user's retention window.
     *
     * @return 200 OK with the updated settings view; 400 if the value is negative
     */
    @PutMapping
    public ResponseEntity<Object> put(@PathVariable String userId,
                                      @RequestBody PrivacySettingsRequest request) {
        if (request == null || request.dataRetentionDays() < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "dataRetentionDays must be >= 0"));
        }
        var settings = UserPrivacySettings.of(userId, request.dataRetentionDays());
        store.save(settings);
        log.info("Updated privacy settings for userId={} dataRetentionDays={}",
                userId, settings.dataRetentionDays());
        return ResponseEntity.ok(toView(settings));
    }

    private static Map<String, Object> toView(UserPrivacySettings settings) {
        return Map.of(
                "userId", settings.userId(),
                "dataRetentionDays", settings.dataRetentionDays(),
                "updatedAt", settings.updatedAt().toString());
    }
}
