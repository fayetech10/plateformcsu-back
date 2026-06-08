package com.csu.gestion_csu.controller;

import com.csu.gestion_csu.service.ActivityFeedService;
import com.csu.gestion_csu.service.ActivityStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Flux d'activité unifié des agents (patients, enrôlements, activités, constats,
 * bons de commande, pointages). Expose le flux en REST (snapshot) et en SSE
 * (temps réel), pour la vue « Activité » et le système de notifications admin.
 */
@RestController
@RequestMapping("/api/admin/activity")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasAnyAuthority('ADMIN','SUPERVISEUR')")
public class ActivityFeedController {

    private final ActivityFeedService activityFeedService;
    private final ActivityStreamService activityStreamService;

    /** Flux récent (snapshot), trié du plus récent au plus ancien. */
    @GetMapping("/feed")
    public ResponseEntity<List<Map<String, Object>>> getFeed(
            @RequestParam(defaultValue = "40") int limit,
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(activityFeedService.feed(limit, type));
    }

    /** Nombre d'événements depuis un horodatage (badge de notification). */
    @GetMapping("/since")
    public ResponseEntity<Map<String, Object>> countSince(@RequestParam(required = false) String ts) {
        return ResponseEntity.ok(activityFeedService.since(ts));
    }

    /**
     * Flux temps réel (Server-Sent Events). Le serveur pousse les nouveaux
     * événements aux clients connectés. L'authentification se fait via l'en-tête
     * Authorization ou le paramètre {@code ?token=} (EventSource ne pouvant pas
     * envoyer d'en-tête).
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return activityStreamService.subscribe();
    }
}
