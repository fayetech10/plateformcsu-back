package com.csu.gestion_csu.controller;

import com.csu.gestion_csu.model.DemandePermission;
import com.csu.gestion_csu.model.Utilisateur;
import com.csu.gestion_csu.repository.DemandePermissionRepository;
import com.csu.gestion_csu.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DemandePermissionController {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final DemandePermissionRepository permissionRepository;
    private final UtilisateurRepository utilisateurRepository;

    private Utilisateur getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Utilisateur) {
            return (Utilisateur) auth.getPrincipal();
        }
        return null;
    }

    /** Création d'une demande par l'utilisateur connecté. */
    @PostMapping
    public ResponseEntity<?> creer(@RequestBody DemandePermission body) {
        Utilisateur user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        if (body.getType() == null || body.getDateDebut() == null || body.getDateFin() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Type, date de début et date de fin sont obligatoires."));
        }
        if (body.getDateFin().isBefore(body.getDateDebut())) {
            return ResponseEntity.badRequest().body(Map.of("message", "La date de fin ne peut pas précéder la date de début."));
        }

        DemandePermission demande = DemandePermission.builder()
                .agentId(user.getId())
                .bureauId(user.getBureauId())
                .type(body.getType())
                .dateDebut(body.getDateDebut())
                .dateFin(body.getDateFin())
                .motif(body.getMotif())
                .statut("EN_ATTENTE")
                .dateDemande(LocalDateTime.now())
                .build();
        permissionRepository.save(demande);
        return ResponseEntity.ok(toRow(demande, null));
    }

    /** Demandes de l'utilisateur connecté. */
    @GetMapping("/me")
    public ResponseEntity<List<Map<String, Object>>> mesDemandes() {
        Utilisateur user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        Map<Long, String> nomById = nomById();
        List<Map<String, Object>> list = permissionRepository
                .findByAgentIdOrderByDateDemandeDesc(user.getId())
                .stream().map(d -> toRow(d, nomById)).collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    /** Annulation d'une demande encore en attente (par son auteur). */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> annuler(@PathVariable Long id) {
        Utilisateur user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return permissionRepository.findById(id).map(d -> {
            boolean isAdmin = "ADMIN".equals(user.getRole());
            if (!isAdmin && !user.getId().equals(d.getAgentId())) {
                return ResponseEntity.status(403).body(Map.of("message", "Action non autorisée."));
            }
            if (!"EN_ATTENTE".equals(d.getStatut()) && !isAdmin) {
                return ResponseEntity.badRequest().body(Map.of("message", "Seules les demandes en attente peuvent être annulées."));
            }
            permissionRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Demande supprimée."));
        }).orElse(ResponseEntity.notFound().build());
    }

    /* ───────────────── Administration ───────────────── */

    /** Liste de toutes les demandes (filtre statut optionnel). */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> toutes(@RequestParam(required = false) String statut) {
        Map<Long, String> nomById = nomById();
        List<DemandePermission> demandes = (statut != null && !statut.isBlank())
                ? permissionRepository.findByStatutOrderByDateDemandeDesc(statut)
                : permissionRepository.findAllByOrderByDateDemandeDesc();
        return ResponseEntity.ok(demandes.stream().map(d -> toRow(d, nomById)).collect(Collectors.toList()));
    }

    /** Nombre de demandes en attente (badge). */
    @GetMapping("/count-attente")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, Object>> countAttente() {
        return ResponseEntity.ok(Map.of("enAttente", permissionRepository.countByStatut("EN_ATTENTE")));
    }

    @PutMapping("/{id}/approuver")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> approuver(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return traiter(id, "APPROUVEE", body);
    }

    @PutMapping("/{id}/refuser")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> refuser(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return traiter(id, "REFUSEE", body);
    }

    private ResponseEntity<?> traiter(Long id, String statut, Map<String, String> body) {
        Utilisateur user = getCurrentUser();
        return permissionRepository.findById(id).map(d -> {
            if (!"EN_ATTENTE".equals(d.getStatut())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Cette demande a déjà été traitée."));
            }
            d.setStatut(statut);
            d.setTraiteePar(user != null ? user.getId() : null);
            d.setDateTraitement(LocalDateTime.now());
            if (body != null) {
                d.setCommentaireAdmin(body.get("commentaire"));
            }
            permissionRepository.save(d);
            return ResponseEntity.ok(toRow(d, nomById()));
        }).orElse(ResponseEntity.notFound().build());
    }

    /* ───────────────── Helpers ───────────────── */

    private Map<Long, String> nomById() {
        return utilisateurRepository.findAll().stream()
                .collect(Collectors.toMap(Utilisateur::getId, u -> u.getPrenom() + " " + u.getNom(), (a, b) -> a));
    }

    private Map<String, Object> toRow(DemandePermission d, Map<Long, String> nomById) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", d.getId());
        m.put("agentId", d.getAgentId());
        if (nomById != null) {
            m.put("agentNom", nomById.getOrDefault(d.getAgentId(), "Agent #" + d.getAgentId()));
            if (d.getTraiteePar() != null) {
                m.put("traiteeParNom", nomById.getOrDefault(d.getTraiteePar(), null));
            }
        }
        m.put("bureauId", d.getBureauId());
        m.put("type", d.getType());
        m.put("dateDebut", d.getDateDebut().toString());
        m.put("dateFin", d.getDateFin().toString());
        m.put("motif", d.getMotif());
        m.put("statut", d.getStatut());
        m.put("dateDemande", d.getDateDemande() == null ? null : d.getDateDemande().format(DT));
        m.put("dateTraitement", d.getDateTraitement() == null ? null : d.getDateTraitement().format(DT));
        m.put("commentaireAdmin", d.getCommentaireAdmin());
        return m;
    }
}
