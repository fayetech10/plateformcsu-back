package com.csu.gestion_csu.controller;

import com.csu.gestion_csu.model.Bureau;
import com.csu.gestion_csu.model.Pointage;
import com.csu.gestion_csu.model.Utilisateur;
import com.csu.gestion_csu.repository.BureauRepository;
import com.csu.gestion_csu.repository.PointageRepository;
import com.csu.gestion_csu.repository.UtilisateurRepository;
import com.csu.gestion_csu.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pointages")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PointageController {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final PointageRepository pointageRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final BureauRepository bureauRepository;

    /** DTO de pointage avec coordonnées géographiques (optionnelles). */
    @lombok.Data
    static class PointageGeoRequest {
        private Double latitude;
        private Double longitude;
        private Double precision; // accuracy en mètres
    }

    private Utilisateur getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Utilisateur) {
            return (Utilisateur) auth.getPrincipal();
        }
        return null;
    }

    /** État de pointage du jour pour l'utilisateur connecté. */
    @GetMapping("/me/today")
    public ResponseEntity<Map<String, Object>> getMyToday() {
        Utilisateur user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        Optional<Pointage> pointage = pointageRepository.findByAgentIdAndDatePointage(user.getId(), LocalDate.now());
        Map<String, Object> result = new HashMap<>();
        result.put("date", LocalDate.now().toString());
        result.put("aPointeArrivee", pointage.isPresent());
        result.put("aPointeDepart", pointage.map(p -> p.getHeureDepart() != null).orElse(false));
        result.put("heureArrivee", pointage.map(p -> p.getHeureArrivee().format(HM)).orElse(null));
        result.put("heureDepart", pointage.map(p -> p.getHeureDepart() == null ? null : p.getHeureDepart().format(HM)).orElse(null));
        return ResponseEntity.ok(result);
    }

    /** Pointage d'arrivée (utilisateur connecté), avec contrôle de géolocalisation. */
    @PostMapping("/arrivee")
    public ResponseEntity<?> pointerArrivee(@RequestBody(required = false) PointageGeoRequest geo) {
        Utilisateur user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        LocalDate today = LocalDate.now();
        Optional<Pointage> existing = pointageRepository.findByAgentIdAndDatePointage(user.getId(), today);
        if (existing.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Vous avez déjà pointé votre arrivée aujourd'hui."));
        }

        // ── Contrôle de géolocalisation (arrivée) ──
        GeoResult r = evaluerGeo(geo, user.getBureauId());

        // Refus si position non vérifiée ou hors de la zone du bureau.
        ResponseEntity<Map<String, Object>> refus = verifierRefus(r, "votre arrivée");
        if (refus != null) {
            return refus;
        }

        Pointage pointage = Pointage.builder()
                .agentId(user.getId())
                .bureauId(user.getBureauId())
                .datePointage(today)
                .heureArrivee(LocalDateTime.now())
                .build();

        pointage.setLatitude(r.latitude);
        pointage.setLongitude(r.longitude);
        pointage.setPrecision(r.precision);
        pointage.setDistanceMetres(r.distanceMetres);
        pointage.setHorsZone(r.horsZone);
        pointage.setPositionVerifiee(r.positionVerifiee);

        pointageRepository.save(pointage);

        Map<String, Object> body = new HashMap<>();
        body.put("message", "Arrivée enregistrée.");
        body.put("heureArrivee", pointage.getHeureArrivee().format(HM));
        body.put("horsZone", r.horsZone);
        body.put("positionVerifiee", r.positionVerifiee);
        body.put("distanceMetres", r.distanceMetres);
        return ResponseEntity.ok(body);
    }

    /** Marge maximale (m) ajoutée au rayon pour tenir compte de l'imprécision GPS sans permettre de contournement. */
    private static final double MARGE_PRECISION_MAX = 75.0;

    /** Résultat d'une évaluation de géolocalisation. */
    private static class GeoResult {
        Double latitude, longitude, precision, distanceMetres;
        Boolean horsZone, positionVerifiee;
        Integer rayonMetres;
        boolean geofencingActif; // le bureau possède des coordonnées → contrôle de présence activé
    }

    /**
     * Calcule la distance entre une position et le bureau et détermine le hors-zone.
     * On ajoute la précision GPS (plafonnée) au rayon toléré pour limiter les faux positifs.
     */
    private GeoResult evaluerGeo(PointageGeoRequest geo, Long bureauId) {
        GeoResult res = new GeoResult();

        Bureau bureau = (bureauId != null) ? bureauRepository.findById(bureauId).orElse(null) : null;
        res.geofencingActif = bureau != null && bureau.getLatitude() != null && bureau.getLongitude() != null;
        res.rayonMetres = (bureau != null && bureau.getRayonToleranceMetres() != null) ? bureau.getRayonToleranceMetres() : 150;

        if (geo == null || geo.getLatitude() == null || geo.getLongitude() == null) {
            res.positionVerifiee = false;
            return res;
        }
        res.latitude = geo.getLatitude();
        res.longitude = geo.getLongitude();
        res.precision = geo.getPrecision();
        res.positionVerifiee = true;

        if (!res.geofencingActif) {
            // Bureau sans coordonnées configurées : impossible de juger la présence
            return res;
        }

        double distance = GeoUtils.distanceMetres(
                geo.getLatitude(), geo.getLongitude(),
                bureau.getLatitude(), bureau.getLongitude());
        res.distanceMetres = distance;

        double marge = (geo.getPrecision() != null) ? Math.min(geo.getPrecision(), MARGE_PRECISION_MAX) : 0;
        res.horsZone = distance > (res.rayonMetres + marge);
        return res;
    }

    /**
     * Détermine si le pointage doit être refusé pour un bureau géolocalisé :
     *  - localisation indisponible (GPS coupé/refusé) → refus ;
     *  - agent hors de la zone autorisée → refus.
     * Renvoie {@code null} si le pointage est autorisé.
     *
     * @param action libellé inséré dans le message (« votre arrivée » / « votre départ »)
     */
    private ResponseEntity<Map<String, Object>> verifierRefus(GeoResult r, String action) {
        if (!r.geofencingActif) {
            return null; // contrôle de présence non configuré pour ce bureau → autorisé
        }
        if (Boolean.FALSE.equals(r.positionVerifiee)) {
            Map<String, Object> refus = new HashMap<>();
            refus.put("message", "Pointage refusé : votre position n'a pas pu être vérifiée. "
                    + "Activez la localisation (GPS) et autorisez l'accès à votre position dans le navigateur, "
                    + "puis réessayez de pointer " + action + ".");
            refus.put("horsZone", false);
            refus.put("positionRequise", true);
            refus.put("rayonMetres", r.rayonMetres);
            return ResponseEntity.badRequest().body(refus);
        }
        if (Boolean.TRUE.equals(r.horsZone)) {
            Map<String, Object> refus = new HashMap<>();
            refus.put("message", "Pointage refusé : vous êtes à environ " + Math.round(r.distanceMetres)
                    + " m du bureau, au-delà de la limite autorisée de " + r.rayonMetres + " m. "
                    + "Rapprochez-vous de votre lieu de travail pour pointer " + action + ".");
            refus.put("horsZone", true);
            refus.put("distanceMetres", Math.round(r.distanceMetres));
            refus.put("rayonMetres", r.rayonMetres);
            return ResponseEntity.badRequest().body(refus);
        }
        return null;
    }

    /** Pointage de départ (utilisateur connecté), avec contrôle de géolocalisation. */
    @PostMapping("/depart")
    public ResponseEntity<?> pointerDepart(@RequestBody(required = false) PointageGeoRequest geo) {
        Utilisateur user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        Optional<Pointage> existing = pointageRepository.findByAgentIdAndDatePointage(user.getId(), LocalDate.now());
        if (existing.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Vous devez d'abord pointer votre arrivée."));
        }
        Pointage pointage = existing.get();
        if (pointage.getHeureDepart() != null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Vous avez déjà pointé votre départ aujourd'hui."));
        }

        // ── Contrôle de géolocalisation (départ) ──
        GeoResult r = evaluerGeo(geo, user.getBureauId());

        // Refus si position non vérifiée ou hors de la zone du bureau.
        ResponseEntity<Map<String, Object>> refus = verifierRefus(r, "votre départ");
        if (refus != null) {
            return refus;
        }

        pointage.setHeureDepart(LocalDateTime.now());
        pointage.setLatitudeDepart(r.latitude);
        pointage.setLongitudeDepart(r.longitude);
        pointage.setPrecisionDepart(r.precision);
        pointage.setDistanceMetresDepart(r.distanceMetres);
        pointage.setHorsZoneDepart(r.horsZone);
        pointage.setPositionVerifieeDepart(r.positionVerifiee);

        pointageRepository.save(pointage);

        Map<String, Object> body = new HashMap<>();
        body.put("message", "Départ enregistré.");
        body.put("heureDepart", pointage.getHeureDepart().format(HM));
        body.put("horsZone", r.horsZone);
        body.put("positionVerifiee", r.positionVerifiee);
        body.put("distanceMetres", r.distanceMetres);
        return ResponseEntity.ok(body);
    }

    /** Historique de pointage de l'utilisateur connecté. */
    @GetMapping("/me")
    public ResponseEntity<List<Map<String, Object>>> getMyHistory() {
        Utilisateur user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        List<Map<String, Object>> history = pointageRepository
                .findByAgentIdOrderByDatePointageDescHeureArriveeDesc(user.getId())
                .stream().map(this::toRow).collect(Collectors.toList());
        return ResponseEntity.ok(history);
    }

    /**
     * Restitution des pointages d'un jour donné (par défaut aujourd'hui).
     * Réservé ADMIN / SUPERVISEUR — utilisé par le tableau de bord admin.
     */
    @GetMapping("/jour")
    @PreAuthorize("hasAnyAuthority('ADMIN','SUPERVISEUR')")
    public ResponseEntity<Map<String, Object>> getPointagesDuJour(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate jour = (date != null) ? date : LocalDate.now();

        // Map agentId -> "Prenom Nom" et agentId -> bureauId
        List<Utilisateur> utilisateurs = utilisateurRepository.findAll();
        Map<Long, String> nomById = utilisateurs.stream()
                .collect(Collectors.toMap(Utilisateur::getId, u -> u.getPrenom() + " " + u.getNom(), (a, b) -> a));

        List<Pointage> pointages = pointageRepository.findByDatePointageOrderByHeureArriveeDesc(jour);

        List<Map<String, Object>> rows = pointages.stream().map(p -> {
            Map<String, Object> m = toRow(p);
            m.put("agentNom", nomById.getOrDefault(p.getAgentId(), "Agent #" + p.getAgentId()));
            return m;
        }).collect(Collectors.toList());

        long totalAgents = utilisateurs.stream().filter(u -> "AGENT".equals(u.getRole())).count();
        long presents = pointages.size();
        long partis = pointages.stream().filter(p -> p.getHeureDepart() != null).count();
        long enService = presents - partis;

        Map<String, Object> result = new HashMap<>();
        result.put("date", jour.toString());
        result.put("totalAgents", totalAgents);
        result.put("presents", presents);
        result.put("partis", partis);
        result.put("enService", enService);
        result.put("absents", Math.max(0, totalAgents - presents));
        result.put("pointages", rows);
        return ResponseEntity.ok(result);
    }

    /**
     * Historique des pointages sur une période (par défaut les 14 derniers jours).
     * Réservé ADMIN / SUPERVISEUR.
     */
    @GetMapping("/historique")
    @PreAuthorize("hasAnyAuthority('ADMIN','SUPERVISEUR')")
    public ResponseEntity<List<Map<String, Object>>> getHistorique(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {

        LocalDate dateFin = (fin != null) ? fin : LocalDate.now();
        LocalDate dateDebut = (debut != null) ? debut : dateFin.minusDays(13);

        Map<Long, String> nomById = utilisateurRepository.findAll().stream()
                .collect(Collectors.toMap(Utilisateur::getId, u -> u.getPrenom() + " " + u.getNom(), (a, b) -> a));

        List<Map<String, Object>> rows = pointageRepository
                .findByDatePointageBetweenOrderByDatePointageDescHeureArriveeDesc(dateDebut, dateFin)
                .stream().map(p -> {
                    Map<String, Object> m = toRow(p);
                    m.put("agentNom", nomById.getOrDefault(p.getAgentId(), "Agent #" + p.getAgentId()));
                    return m;
                }).collect(Collectors.toList());

        return ResponseEntity.ok(rows);
    }

    private Map<String, Object> toRow(Pointage p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("agentId", p.getAgentId());
        m.put("bureauId", p.getBureauId());
        m.put("date", p.getDatePointage().toString());
        m.put("heureArrivee", p.getHeureArrivee() == null ? null : p.getHeureArrivee().format(HM));
        m.put("heureDepart", p.getHeureDepart() == null ? null : p.getHeureDepart().format(HM));
        m.put("statut", p.getHeureDepart() == null ? "EN_SERVICE" : "PARTI");
        m.put("horsZone", p.getHorsZone());
        m.put("positionVerifiee", p.getPositionVerifiee());
        m.put("distanceMetres", p.getDistanceMetres() == null ? null : Math.round(p.getDistanceMetres()));
        m.put("latitude", p.getLatitude());
        m.put("longitude", p.getLongitude());
        m.put("horsZoneDepart", p.getHorsZoneDepart());
        m.put("positionVerifieeDepart", p.getPositionVerifieeDepart());
        m.put("distanceMetresDepart", p.getDistanceMetresDepart() == null ? null : Math.round(p.getDistanceMetresDepart()));
        return m;
    }
}
