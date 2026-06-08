package com.csu.gestion_csu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Diffuse le flux d'activité en temps réel via Server-Sent Events (SSE).
 * Le serveur interroge périodiquement la base et pousse uniquement les nouveaux
 * événements aux clients connectés (push serveur → navigateur), évitant le
 * polling côté client.
 */
@Service
@RequiredArgsConstructor
public class ActivityStreamService {

    private final ActivityFeedService activityFeedService;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /** Horodatage du dernier événement déjà diffusé (ne rediffuse pas l'historique au démarrage). */
    private volatile LocalDateTime lastBroadcast;
    private volatile boolean initialized = false;

    private static final long TIMEOUT_MS = 30 * 60 * 1000L; // 30 min

    /** Enregistre un nouveau client SSE. */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> { emitter.complete(); emitters.remove(emitter); });
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);

        // Évènement initial de confirmation de connexion
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    /** Diffuse les nouveaux événements toutes les 4 secondes. */
    @Scheduled(fixedDelay = 4000)
    public void broadcastNew() {
        // Au premier passage, on cale le curseur sans rien diffuser (pas d'inondation au démarrage).
        if (!initialized) {
            lastBroadcast = activityFeedService.latestTimestamp();
            initialized = true;
            return;
        }
        if (emitters.isEmpty()) {
            // Personne n'écoute : on avance quand même le curseur pour ne pas accumuler.
            lastBroadcast = activityFeedService.latestTimestamp();
            return;
        }

        List<ActivityFeedService.Event> nouveaux = activityFeedService.newerThan(lastBroadcast);
        if (nouveaux.isEmpty()) return;

        for (ActivityFeedService.Event e : nouveaux) {
            if (e.timestamp != null && (lastBroadcast == null || e.timestamp.isAfter(lastBroadcast))) {
                lastBroadcast = e.timestamp;
            }
            diffuser("activity", e.toMap());
        }
    }

    /** Heartbeat toutes les 25 s pour maintenir la connexion et purger les clients morts. */
    @Scheduled(fixedDelay = 25000)
    public void heartbeat() {
        diffuser("ping", "{}");
    }

    private void diffuser(String event, Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (Exception ex) {
                emitter.complete();
                emitters.remove(emitter);
            }
        }
    }
}
