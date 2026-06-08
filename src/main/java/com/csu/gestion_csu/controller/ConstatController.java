package com.csu.gestion_csu.controller;

import com.csu.gestion_csu.model.Constat;
import com.csu.gestion_csu.repository.ConstatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/constats")
@RequiredArgsConstructor
public class ConstatController {

    private final ConstatRepository constatRepository;

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
    public ResponseEntity<org.springframework.data.domain.Page<Constat>> getConstats(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String priorite,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(constatRepository.searchConstats(getBureauIdFilter(), statut, priorite, search, org.springframework.data.domain.PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Constat> getConstat(@PathVariable Long id) {
        return constatRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Constat> createConstat(@RequestBody Constat constat) {
        com.csu.gestion_csu.model.Utilisateur user = getCurrentUser();
        if (user != null) {
            if (constat.getResponsableId() == null) {
                constat.setResponsableId(user.getId());
            }
            if (constat.getBureauCsuId() == null) {
                constat.setBureauCsuId(user.getBureauId());
            }
        }
        if (constat.getDateConstat() == null) {
            constat.setDateConstat(java.time.LocalDateTime.now());
        }
        return ResponseEntity.ok(constatRepository.save(constat));
    }


    @PutMapping("/{id}")
    public ResponseEntity<?> updateConstat(@PathVariable Long id, @RequestBody Constat constatDetails) {
        com.csu.gestion_csu.model.Utilisateur user = getCurrentUser();
        return constatRepository.findById(id)
                .map(existing -> {
                    if (user != null && "AGENT".equals(user.getRole())) {
                        if (!user.getId().equals(existing.getResponsableId())) {
                            return ResponseEntity.status(403).body("Vous n'êtes pas autorisé à modifier ce constat car il appartient à un collègue.");
                        }
                    }
                    existing.setPriorite(constatDetails.getPriorite());
                    existing.setStatut(constatDetails.getStatut());
                    existing.setDescription(constatDetails.getDescription());
                    existing.setArchive(constatDetails.isArchive());
                    existing.setPiecesJointes(constatDetails.getPiecesJointes());
                    return ResponseEntity.ok(constatRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/archiver")
    public ResponseEntity<?> archiverConstat(@PathVariable Long id) {
        com.csu.gestion_csu.model.Utilisateur user = getCurrentUser();
        return constatRepository.findById(id)
                .map(existing -> {
                    if (user != null && "AGENT".equals(user.getRole())) {
                        if (!user.getId().equals(existing.getResponsableId())) {
                            return ResponseEntity.status(403).body("Vous n'êtes pas autorisé à archiver ce constat car il appartient à un collègue.");
                        }
                    }
                    existing.setArchive(true);
                    return ResponseEntity.ok(constatRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/attachments")
    public ResponseEntity<?> uploadAttachments(
            @PathVariable Long id,
            @RequestParam("files") org.springframework.web.multipart.MultipartFile[] files) {
        com.csu.gestion_csu.model.Utilisateur user = getCurrentUser();
        return constatRepository.findById(id)
                .map(existing -> {
                    if (user != null && "AGENT".equals(user.getRole())) {
                        if (!user.getId().equals(existing.getResponsableId())) {
                            return ResponseEntity.status(403).body("Vous n'êtes pas autorisé à modifier ce constat car il appartient à un collègue.");
                        }
                    }
                    java.util.List<String> current = new java.util.ArrayList<>();
                    if (existing.getPiecesJointes() != null && !existing.getPiecesJointes().isEmpty()) {
                        current.addAll(java.util.Arrays.asList(existing.getPiecesJointes().split(",")));
                    }
                    for (org.springframework.web.multipart.MultipartFile file : files) {
                        current.add(file.getOriginalFilename());
                    }
                    existing.setPiecesJointes(String.join(",", current));
                    return ResponseEntity.ok(constatRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteConstat(@PathVariable Long id) {
        com.csu.gestion_csu.model.Utilisateur user = getCurrentUser();
        return constatRepository.findById(id)
                .map(existing -> {
                    if (user != null && "AGENT".equals(user.getRole())) {
                        if (!user.getId().equals(existing.getResponsableId())) {
                            return ResponseEntity.status(403).body("Vous n'êtes pas autorisé à supprimer ce constat car il appartient à un collègue.");
                        }
                    }
                    constatRepository.deleteById(id);
                    return ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

}
