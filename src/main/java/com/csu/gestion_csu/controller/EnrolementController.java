package com.csu.gestion_csu.controller;

import com.csu.gestion_csu.model.Bureau;
import com.csu.gestion_csu.model.Enrolement;
import com.csu.gestion_csu.repository.BureauRepository;
import com.csu.gestion_csu.repository.EnrolementRepository;
import com.csu.gestion_csu.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/enrolements")
@RequiredArgsConstructor
public class EnrolementController {

    private final EnrolementRepository enrolementRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final BureauRepository bureauRepository;
    private final com.csu.gestion_csu.service.KoboSyncService koboSyncService;

    /** Résout et renseigne le nom de l'agent et du bureau (champs d'affichage). */
    private Enrolement enrichir(Enrolement e) {
        if (e == null) return null;
        if (e.getAgentId() != null) {
            utilisateurRepository.findById(e.getAgentId())
                    .ifPresent(u -> e.setAgentNom(u.getPrenom() + " " + u.getNom()));
        }
        if (e.getBureauCsuId() != null) {
            bureauRepository.findById(e.getBureauCsuId())
                    .ifPresent(b -> e.setBureauCsuNom(b.getNom()));
        }
        return e;
    }

    /** Données saisies à l'enrôlement : identité du bénéficiaire + personnes à charge. */
    @lombok.Data
    static class EnrolementRequest {
        private String nom;
        private String prenom;
        private String adresse;
        private String telephone;
        private String sexe;
        private LocalDate dateNaissance;
        private String observations;
        // Champs conformes Kobo
        private String regionAffiliation;
        private String organismeAssureur;
        private String ogd;
        private String typeRegime;
        private String typeBeneficiaire;
        private String typeAdhesion;
        private String regionResidence;
        private String departementResidence;
        private String communeResidence;
        private String lieuNaissance;
        private String situationMatrimoniale;
        private String secteurActivite;
        private String autreTelephone;
        private String typePieceIdentite;
        private String numeroPiece1;
        private String numeroPiece2;
        private String numeroPiece3;
        private Integer montantFraisAdhesion;
        private Integer montantCotisation;
        private String moyenPaiement;
        private Integer montantVersement;
        private String statutPaiement;
        private List<PersonneAChargeRequest> personnesACharge;
    }

    /** Personne à charge saisie sur l'enrôlement. */
    @lombok.Data
    static class PersonneAChargeRequest {
        private String prenom;
        private String nom;
        private String sexe;
        private LocalDate dateNaissance;
        private String lienParente;
        private String telephone;
    }

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
    public ResponseEntity<org.springframework.data.domain.Page<Enrolement>> getEnrolements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String search) {
        org.springframework.data.domain.Page<Enrolement> result =
                enrolementRepository.searchEnrolements(getBureauIdFilter(), statut, search, org.springframework.data.domain.PageRequest.of(page, size));
        result.getContent().forEach(this::enrichir);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Enrolement> getEnrolement(@PathVariable Long id) {
        return enrolementRepository.findById(id)
                .map(this::enrichir)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createEnrolement(@RequestBody EnrolementRequest req) {
        if (req.getNom() == null || req.getNom().isBlank()
                || req.getPrenom() == null || req.getPrenom().isBlank()
                || req.getTelephone() == null || req.getTelephone().isBlank()
                || req.getSexe() == null || req.getSexe().isBlank()
                || req.getDateNaissance() == null) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "message", "Nom, prénom, téléphone, sexe et date de naissance sont obligatoires."));
        }

        com.csu.gestion_csu.model.Utilisateur user = getCurrentUser();
        Long agentId = (user != null) ? user.getId() : null;
        Long bureauId = (user != null) ? user.getBureauId() : null;
        int year = LocalDate.now().getYear();
        long stamp = System.currentTimeMillis();

        // L'enrôlement est autonome : on enregistre l'identité du bénéficiaire
        // directement sur l'enrôlement, sans créer de patient.
        Enrolement enrolement = new Enrolement();
        enrolement.setPrenom(req.getPrenom().trim());
        enrolement.setNom(req.getNom().trim());
        enrolement.setSexe(req.getSexe());
        enrolement.setDateNaissance(req.getDateNaissance());
        enrolement.setTelephone(req.getTelephone());
        enrolement.setAdresse(req.getAdresse());
        enrolement.setNumeroBeneficiaire("BEN-" + year + "-" + (stamp % 100000));
        enrolement.setStatut("EN_COURS");
        enrolement.setDateEnrolement(LocalDateTime.now());
        enrolement.setObservations(req.getObservations());
        enrolement.setAgentId(agentId);
        enrolement.setBureauCsuId(bureauId);
        // Champs conformes Kobo
        enrolement.setRegionAffiliation(req.getRegionAffiliation());
        enrolement.setOrganismeAssureur(req.getOrganismeAssureur());
        enrolement.setOgd(req.getOgd());
        enrolement.setTypeRegime(req.getTypeRegime());
        enrolement.setTypeBeneficiaire(req.getTypeBeneficiaire());
        enrolement.setTypeAdhesion(req.getTypeAdhesion());
        enrolement.setRegionResidence(req.getRegionResidence());
        enrolement.setDepartementResidence(req.getDepartementResidence());
        enrolement.setCommuneResidence(req.getCommuneResidence());
        enrolement.setLieuNaissance(req.getLieuNaissance());
        enrolement.setSituationMatrimoniale(req.getSituationMatrimoniale());
        enrolement.setSecteurActivite(req.getSecteurActivite());
        enrolement.setAutreTelephone(req.getAutreTelephone());
        enrolement.setTypePieceIdentite(req.getTypePieceIdentite());
        enrolement.setNumeroPiece1(req.getNumeroPiece1());
        enrolement.setNumeroPiece2(req.getNumeroPiece2());
        enrolement.setNumeroPiece3(req.getNumeroPiece3());
        enrolement.setMontantFraisAdhesion(req.getMontantFraisAdhesion());
        enrolement.setMontantCotisation(req.getMontantCotisation());
        enrolement.setMoyenPaiement(req.getMoyenPaiement());
        enrolement.setMontantVersement(req.getMontantVersement());
        enrolement.setStatutPaiement(req.getStatutPaiement());
        enrolement.setKoboSyncStatus(com.csu.gestion_csu.service.KoboSyncService.STATUT_EN_ATTENTE);

        // Personnes à charge (ignore les lignes vides)
        if (req.getPersonnesACharge() != null) {
            for (PersonneAChargeRequest d : req.getPersonnesACharge()) {
                if (d == null) continue;
                boolean vide = (d.getNom() == null || d.getNom().isBlank())
                        && (d.getPrenom() == null || d.getPrenom().isBlank());
                if (vide) continue;
                com.csu.gestion_csu.model.PersonneACharge p = new com.csu.gestion_csu.model.PersonneACharge();
                p.setPrenom(d.getPrenom() != null ? d.getPrenom().trim() : null);
                p.setNom(d.getNom() != null ? d.getNom().trim() : null);
                p.setSexe(d.getSexe());
                p.setDateNaissance(d.getDateNaissance());
                p.setLienParente(d.getLienParente());
                p.setTelephone(d.getTelephone());
                enrolement.getPersonnesACharge().add(p);
            }
        }

        Enrolement saved = enrolementRepository.save(enrolement);
        // Pousse l'enrôlement vers KoboToolbox sans bloquer la réponse à l'agent.
        koboSyncService.syncAsync(saved.getId());
        return ResponseEntity.ok(enrichir(saved));
    }

    /** Renvoie (ou rejoue) la synchronisation d'un enrôlement vers KoboToolbox (Form 1). */
    @PostMapping("/{id}/sync-kobo")
    public ResponseEntity<?> syncKobo(@PathVariable Long id) {
        Enrolement e = koboSyncService.sync(id);
        if (e == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(enrichir(e));
    }

    /** Envoie les personnes à charge vers le formulaire "Rajout de personnes à charge" (Form 2). */
    @PostMapping("/{id}/sync-kobo-rajout")
    public ResponseEntity<?> syncKoboRajout(@PathVariable Long id) {
        Enrolement e = koboSyncService.syncRajout(id);
        if (e == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(enrichir(e));
    }


    @PutMapping("/{id}")
    public ResponseEntity<?> updateEnrolement(@PathVariable Long id, @RequestBody Enrolement enrolementDetails) {
        com.csu.gestion_csu.model.Utilisateur user = getCurrentUser();
        return enrolementRepository.findById(id)
                .map(existing -> {
                    if (user != null && "AGENT".equals(user.getRole())) {
                        if (!user.getId().equals(existing.getAgentId())) {
                            return ResponseEntity.status(403).body("Vous n'êtes pas autorisé à modifier cet enrôlement car il appartient à un collègue.");
                        }
                    }
                    existing.setStatut(enrolementDetails.getStatut());
                    existing.setObservations(enrolementDetails.getObservations());
                    return ResponseEntity.ok(enrolementRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> payload) {
        com.csu.gestion_csu.model.Utilisateur user = getCurrentUser();
        String statut = payload.get("statut");
        String observations = payload.get("observations");
        return enrolementRepository.findById(id)
                .map(existing -> {
                    if (user != null && "AGENT".equals(user.getRole())) {
                        if (!user.getId().equals(existing.getAgentId())) {
                            return ResponseEntity.status(403).body("Vous n'êtes pas autorisé à modifier le statut de cet enrôlement car il appartient à un collègue.");
                        }
                    }
                    if (statut != null) {
                        existing.setStatut(statut);
                    }
                    if (observations != null) {
                        existing.setObservations(observations);
                    }
                    return ResponseEntity.ok(enrolementRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEnrolement(@PathVariable Long id) {
        com.csu.gestion_csu.model.Utilisateur user = getCurrentUser();
        return enrolementRepository.findById(id)
                .map(existing -> {
                    if (user != null && "AGENT".equals(user.getRole())) {
                        if (!user.getId().equals(existing.getAgentId())) {
                            return ResponseEntity.status(403).body("Vous n'êtes pas autorisé à supprimer cet enrôlement car il appartient à un collègue.");
                        }
                    }
                    enrolementRepository.deleteById(id);
                    return ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

}
