package com.csu.gestion_csu.controller;

import com.csu.gestion_csu.model.Activite;
import com.csu.gestion_csu.repository.ActiviteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activites")
@RequiredArgsConstructor
public class ActiviteController {

    private final ActiviteRepository activiteRepository;

    private com.csu.gestion_csu.model.Utilisateur getCurrentUser() {
        org.springframework.security.core.Authentication authentication = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof com.csu.gestion_csu.model.Utilisateur) {
            return (com.csu.gestion_csu.model.Utilisateur) authentication.getPrincipal();
        }
        return null;
    }

    private Long getBureauIdFilter() {
        com.csu.gestion_csu.model.Utilisateur user = getCurrentUser();
        if (user != null && "AGENT".equals(user.getRole())) {
            return user.getBureauId();
        }
        return null;
    }

    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<Activite>> getActivites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String typeActivite,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(activiteRepository.searchActivites(
                getBureauIdFilter(), typeActivite, statut, search,
                org.springframework.data.domain.PageRequest.of(page, size)));
    }

    /** Statistiques d'activités (filtrées par bureau pour les agents). */
    @GetMapping("/stats")
    public ResponseEntity<java.util.Map<String, Object>> getStats() {
        Long bureauId = getBureauIdFilter();
        List<Activite> activites = (bureauId != null)
                ? activiteRepository.findByBureauCsuIdOrderByDateActiviteDesc(bureauId)
                : activiteRepository.findAll();

        long total = activites.size();
        long totalParticipants = activites.stream()
                .mapToLong(a -> a.getNombreParticipants() == null ? 0 : a.getNombreParticipants())
                .sum();

        java.util.Map<String, Long> parType = new java.util.LinkedHashMap<>();
        for (String t : new String[]{"SENSIBILISATION", "FORMATION", "REUNION", "VISITE_TERRAIN", "ASSISTANCE_ADMINISTRATIVE"}) {
            parType.put(t, 0L);
        }
        java.util.Map<String, Long> parStatut = new java.util.LinkedHashMap<>();
        parStatut.put("PLANIFIEE", 0L);
        parStatut.put("REALISEE", 0L);
        parStatut.put("ANNULEE", 0L);

        for (Activite a : activites) {
            if (a.getTypeActivite() != null) {
                parType.merge(a.getTypeActivite(), 1L, Long::sum);
            }
            String st = (a.getStatut() == null || a.getStatut().isEmpty()) ? "REALISEE" : a.getStatut();
            parStatut.merge(st, 1L, Long::sum);
        }

        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("total", total);
        stats.put("totalParticipants", totalParticipants);
        stats.put("parType", parType);
        stats.put("parStatut", parStatut);
        return ResponseEntity.ok(stats);
    }

    /** Prochaines activités planifiées (à venir), filtrées par bureau. */
    @GetMapping("/a-venir")
    public ResponseEntity<List<Activite>> getAVenir() {
        Long bureauId = getBureauIdFilter();
        List<Activite> activites = (bureauId != null)
                ? activiteRepository.findByBureauCsuIdOrderByDateActiviteDesc(bureauId)
                : activiteRepository.findAll();

        java.time.LocalDateTime now = java.time.LocalDateTime.now().toLocalDate().atStartOfDay();
        List<Activite> aVenir = activites.stream()
                .filter(a -> "PLANIFIEE".equals(a.getStatut()))
                .filter(a -> a.getDateActivite() != null && !a.getDateActivite().isBefore(now))
                .sorted(java.util.Comparator.comparing(Activite::getDateActivite))
                .limit(6)
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(aVenir);
    }

    /** Activités sur une période (pour la vue agenda/calendrier), filtrées par bureau. */
    @GetMapping("/calendrier")
    public ResponseEntity<List<Activite>> getCalendrier(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate debut,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fin) {
        Long bureauId = getBureauIdFilter();
        List<Activite> list = activiteRepository.findActivitesForReport(
                debut.atStartOfDay(), fin.atTime(23, 59, 59), bureauId, null);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Activite> getActivite(@PathVariable Long id) {
        return activiteRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Activite> createActivite(@RequestBody Activite activite) {
        com.csu.gestion_csu.model.Utilisateur user = getCurrentUser();
        if (user != null) {
            if (activite.getAgentId() == null) {
                activite.setAgentId(user.getId());
            }
            if (activite.getBureauCsuId() == null) {
                activite.setBureauCsuId(user.getBureauId());
            }
        }
        if (activite.getDateActivite() == null) {
            activite.setDateActivite(java.time.LocalDateTime.now());
        }
        if (activite.getStatut() == null || activite.getStatut().isEmpty()) {
            activite.setStatut("REALISEE");
        }
        return ResponseEntity.ok(activiteRepository.save(activite));
    }


    @PutMapping("/{id}")
    public ResponseEntity<?> updateActivite(@PathVariable Long id, @RequestBody Activite activiteDetails) {
        com.csu.gestion_csu.model.Utilisateur user = getCurrentUser();
        return activiteRepository.findById(id)
                .map(existing -> {
                    if (user != null && "AGENT".equals(user.getRole())) {
                        if (!user.getId().equals(existing.getAgentId())) {
                            return ResponseEntity.status(403).body("Vous n'êtes pas autorisé à modifier cette activité car elle appartient à un collègue.");
                        }
                    }
                    existing.setTypeActivite(activiteDetails.getTypeActivite());
                    existing.setDescription(activiteDetails.getDescription());
                    existing.setDateActivite(activiteDetails.getDateActivite());
                    existing.setNombreParticipants(activiteDetails.getNombreParticipants());
                    existing.setCommentaires(activiteDetails.getCommentaires());
                    if (activiteDetails.getStatut() != null && !activiteDetails.getStatut().isEmpty()) {
                        existing.setStatut(activiteDetails.getStatut());
                    }
                    return ResponseEntity.ok(activiteRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteActivite(@PathVariable Long id) {
        com.csu.gestion_csu.model.Utilisateur user = getCurrentUser();
        return activiteRepository.findById(id)
                .map(existing -> {
                    if (user != null && "AGENT".equals(user.getRole())) {
                        if (!user.getId().equals(existing.getAgentId())) {
                            return ResponseEntity.status(403).body("Vous n'êtes pas autorisé à supprimer cette activité car elle appartient à un collègue.");
                        }
                    }
                    activiteRepository.deleteById(id);
                    return ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

}
