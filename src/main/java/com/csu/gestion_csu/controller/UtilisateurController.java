package com.csu.gestion_csu.controller;

import com.csu.gestion_csu.model.Utilisateur;
import com.csu.gestion_csu.repository.UtilisateurRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
public class UtilisateurController {

    private final UtilisateurRepository utilisateurRepository;
    private final com.csu.gestion_csu.repository.BureauRepository bureauRepository;
    private final PasswordEncoder passwordEncoder;

    /** Renseigne le nom du bureau (champ d'affichage) à partir du bureauId. */
    private Utilisateur enrichir(Utilisateur u) {
        if (u != null && u.getBureauId() != null) {
            bureauRepository.findById(u.getBureauId())
                    .ifPresent(b -> u.setBureauCsuNom(b.getNom()));
        }
        return u;
    }

    // DTO interne pour lire bureauCsuId depuis le corps de la requête
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class UtilisateurRequest {
        private String nom;
        private String prenom;
        private String username;
        private String password;
        private String role;
        private String email;
        private String telephone;
        private Long bureauCsuId;   // champ envoyé par le frontend
        private Long structureId;
        private boolean actif = true;
    }

    // Retourne une réponse paginée attendue par le frontend
    @GetMapping
    public ResponseEntity<?> getUtilisateurs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        Page<Utilisateur> pageResult;
        if (search != null && !search.trim().isEmpty()) {
            pageResult = utilisateurRepository.findByNomContainingIgnoreCaseOrPrenomContainingIgnoreCaseOrEmailContainingIgnoreCaseOrUsernameContainingIgnoreCase(
                    search, search, search, search, PageRequest.of(page, size));
        } else {
            pageResult = utilisateurRepository.findAll(PageRequest.of(page, size));
        }
        pageResult.getContent().forEach(this::enrichir);
        return ResponseEntity.ok(Map.of(
                "content", pageResult.getContent(),
                "totalElements", pageResult.getTotalElements(),
                "totalPages", pageResult.getTotalPages()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Utilisateur> getUtilisateur(@PathVariable Long id) {
        return utilisateurRepository.findById(id)
                .map(this::enrichir)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Utilisateur> createUtilisateur(@RequestBody UtilisateurRequest req) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setNom(req.getNom());
        utilisateur.setPrenom(req.getPrenom());
        utilisateur.setUsername(req.getUsername());
        utilisateur.setPassword(passwordEncoder.encode(req.getPassword()));
        utilisateur.setRole(req.getRole());
        utilisateur.setEmail(req.getEmail());
        utilisateur.setTelephone(req.getTelephone());
        utilisateur.setBureauId(req.getBureauCsuId());   // mapping bureauCsuId -> bureauId
        utilisateur.setStructureId(req.getStructureId());
        utilisateur.setActif(req.isActif());
        // Nouvel utilisateur : doit changer son mot de passe par défaut à la 1re connexion
        utilisateur.setDoitChangerMotDePasse(true);
        utilisateur.setDateCreation(java.time.LocalDateTime.now());
        return ResponseEntity.ok(enrichir(utilisateurRepository.save(utilisateur)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Utilisateur> updateUtilisateur(@PathVariable Long id, @RequestBody UtilisateurRequest req) {
        return utilisateurRepository.findById(id)
                .map(existing -> {
                    existing.setNom(req.getNom());
                    existing.setPrenom(req.getPrenom());
                    existing.setRole(req.getRole());
                    existing.setEmail(req.getEmail());
                    existing.setTelephone(req.getTelephone());
                    existing.setActif(req.isActif());
                    existing.setBureauId(req.getBureauCsuId());   // mapping bureauCsuId -> bureauId
                    existing.setStructureId(req.getStructureId());
                    if (req.getPassword() != null && !req.getPassword().isEmpty()) {
                        existing.setPassword(passwordEncoder.encode(req.getPassword()));
                        // Réinitialisation par l'admin : l'utilisateur devra le changer
                        existing.setDoitChangerMotDePasse(true);
                    }
                    return ResponseEntity.ok(utilisateurRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/toggle-active")
    public ResponseEntity<Utilisateur> toggleActive(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Object activeVal = payload.get("active");
        if (activeVal == null) {
            activeVal = payload.get("actif");
        }
        boolean active = true;
        if (activeVal instanceof Boolean) {
            active = (Boolean) activeVal;
        } else if (activeVal != null) {
            active = Boolean.parseBoolean(activeVal.toString());
        }
        final boolean newActiveState = active;
        return utilisateurRepository.findById(id)
                .map(existing -> {
                    existing.setActif(newActiveState);
                    return ResponseEntity.ok(utilisateurRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUtilisateur(@PathVariable Long id) {
        if (utilisateurRepository.existsById(id)) {
            utilisateurRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
