package com.csu.gestion_csu.service;

import com.csu.gestion_csu.model.*;
import com.csu.gestion_csu.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Agrège le flux d'activité unifié des agents (patients, enrôlements, activités,
 * constats, bons de commande, pointages). Réutilisé par le contrôleur REST et
 * par le diffuseur SSE temps réel.
 */
@Service
@RequiredArgsConstructor
public class ActivityFeedService {

    private final PatientRepository patientRepository;
    private final EnrolementRepository enrolementRepository;
    private final ActiviteRepository activiteRepository;
    private final ConstatRepository constatRepository;
    private final BonCommandeRepository bonCommandeRepository;
    private final PointageRepository pointageRepository;
    private final UtilisateurRepository utilisateurRepository;

    /** Événement du flux. */
    public static class Event {
        public String id, type, title, description, agentNom, link, tone;
        public LocalDateTime timestamp;

        public Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("id", id);
            m.put("type", type);
            m.put("title", title);
            m.put("description", description);
            m.put("agentNom", agentNom);
            m.put("timestamp", timestamp == null ? null : timestamp.toString());
            m.put("link", link);
            m.put("tone", tone);
            return m;
        }
    }

    private Map<Long, String> agentNoms() {
        return utilisateurRepository.findAll().stream()
                .collect(Collectors.toMap(Utilisateur::getId,
                        u -> u.getPrenom() + " " + u.getNom(), (a, b) -> a));
    }

    private static Event ev(String id, String type, String title, String desc, String agent,
                            LocalDateTime ts, String link, String tone) {
        Event e = new Event();
        e.id = id; e.type = type; e.title = title; e.description = desc;
        e.agentNom = agent; e.timestamp = ts; e.link = link; e.tone = tone;
        return e;
    }

    /** Collecte les événements récents (non triés). */
    public List<Event> collect() {
        Map<Long, String> noms = agentNoms();
        List<Event> events = new ArrayList<>();

        for (Patient p : patientRepository.findTop40ByOrderByDateEnregistrementDesc()) {
            events.add(ev("PATIENT-" + p.getId(), "PATIENT", "Nouveau patient enregistré",
                    (p.getPrenom() + " " + p.getNom()).trim() + " · " + p.getNumeroDossier(),
                    p.getAgentId() == null ? null : noms.get(p.getAgentId()),
                    p.getDateEnregistrement(), "/patients/" + p.getId(), "blue"));
        }

        for (Enrolement en : enrolementRepository.findTop40ByOrderByDateEnrolementDesc()) {
            String benef = ((en.getPrenom() == null ? "" : en.getPrenom()) + " " + (en.getNom() == null ? "" : en.getNom())).trim();
            events.add(ev("ENROLEMENT-" + en.getId(), "ENROLEMENT", "Nouvel enrôlement",
                    benef.isEmpty() ? en.getNumeroBeneficiaire() : benef + " · " + en.getNumeroBeneficiaire(),
                    en.getAgentId() == null ? null : noms.get(en.getAgentId()),
                    en.getDateEnrolement(), "/enrolements", "green"));
        }

        for (Activite a : activiteRepository.findTop40ByOrderByDateActiviteDesc()) {
            events.add(ev("ACTIVITE-" + a.getId(), "ACTIVITE", "Activité : " + a.getTypeActivite(),
                    a.getDescription(),
                    a.getAgentId() == null ? null : noms.get(a.getAgentId()),
                    a.getDateActivite(), "/activites", "purple"));
        }

        for (Constat c : constatRepository.findTop40ByOrderByDateConstatDesc()) {
            events.add(ev("CONSTAT-" + c.getId(), "CONSTAT",
                    "Constat " + (c.getPriorite() != null ? "(" + c.getPriorite() + ")" : ""),
                    c.getReferenceConstat() + " · " + c.getDescription(),
                    c.getResponsableId() == null ? null : noms.get(c.getResponsableId()),
                    c.getDateConstat(), "/constats", "orange"));
        }

        for (BonCommande b : bonCommandeRepository.findTop40ByOrderByDateCreationDesc()) {
            events.add(ev("BON-" + b.getId(), "BON_COMMANDE", "Bon de commande émis",
                    b.getReference() + (b.getPatientNom() != null ? " · " + b.getPatientNom() : ""),
                    b.getAgentNom(), b.getDateCreation(), "/bons-commande/" + b.getId(), "teal"));
        }

        for (Pointage pt : pointageRepository.findTop40ByOrderByHeureArriveeDesc()) {
            if (pt.getHeureDepart() != null) {
                events.add(ev("POINTAGE-DEP-" + pt.getId(), "POINTAGE", "Pointage — départ",
                        Boolean.TRUE.equals(pt.getHorsZoneDepart()) ? "Départ hors zone" : "Départ enregistré",
                        pt.getAgentId() == null ? null : noms.get(pt.getAgentId()),
                        pt.getHeureDepart(), "/pointage",
                        Boolean.TRUE.equals(pt.getHorsZoneDepart()) ? "red" : "slate"));
            }
            if (pt.getHeureArrivee() != null) {
                events.add(ev("POINTAGE-ARR-" + pt.getId(), "POINTAGE", "Pointage — arrivée",
                        Boolean.TRUE.equals(pt.getHorsZone()) ? "Arrivée hors zone" : "Arrivée enregistrée",
                        pt.getAgentId() == null ? null : noms.get(pt.getAgentId()),
                        pt.getHeureArrivee(), "/pointage",
                        Boolean.TRUE.equals(pt.getHorsZone()) ? "red" : "slate"));
            }
        }

        return events;
    }

    /** Flux trié du plus récent au plus ancien, filtrable par type, limité. */
    public List<Map<String, Object>> feed(int limit, String type) {
        return collect().stream()
                .filter(e -> e.timestamp != null)
                .filter(e -> type == null || type.isBlank() || type.equals(e.type))
                .sorted(Comparator.comparing((Event e) -> e.timestamp).reversed())
                .limit(Math.max(1, Math.min(limit, 100)))
                .map(Event::toMap)
                .collect(Collectors.toList());
    }

    /** Compte les événements postérieurs à l'horodatage donné + l'horodatage le plus récent. */
    public Map<String, Object> since(String ts) {
        List<Event> events = collect().stream()
                .filter(e -> e.timestamp != null)
                .collect(Collectors.toList());

        LocalDateTime depuis = null;
        if (ts != null && !ts.isBlank()) {
            try { depuis = LocalDateTime.parse(ts); } catch (Exception ignored) { }
        }
        final LocalDateTime ref = depuis;

        long count = (ref == null) ? 0 : events.stream().filter(e -> e.timestamp.isAfter(ref)).count();
        Optional<LocalDateTime> latest = events.stream().map(e -> e.timestamp).max(Comparator.naturalOrder());

        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        result.put("latest", latest.map(LocalDateTime::toString).orElse(null));
        return result;
    }

    /** Événements postérieurs à un horodatage, triés du plus ancien au plus récent (pour diffusion SSE). */
    public List<Event> newerThan(LocalDateTime ref) {
        return collect().stream()
                .filter(e -> e.timestamp != null && (ref == null || e.timestamp.isAfter(ref)))
                .sorted(Comparator.comparing((Event e) -> e.timestamp))
                .collect(Collectors.toList());
    }

    /** Horodatage de l'événement le plus récent (ou null). */
    public LocalDateTime latestTimestamp() {
        return collect().stream()
                .map(e -> e.timestamp)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
