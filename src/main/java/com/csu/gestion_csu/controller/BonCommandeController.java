package com.csu.gestion_csu.controller;

import com.csu.gestion_csu.model.BonCommande;
import com.csu.gestion_csu.model.Utilisateur;
import com.csu.gestion_csu.repository.BonCommandeRepository;
import com.csu.gestion_csu.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

@RestController
@RequestMapping("/api/bons-commande")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BonCommandeController {

    private final BonCommandeRepository bonCommandeRepository;
    private final PatientRepository patientRepository;

    private Utilisateur getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Utilisateur) {
            return (Utilisateur) authentication.getPrincipal();
        }
        return null;
    }

    /** Génère une référence unique de la forme BC-2026-000123. */
    private synchronized String genererReference() {
        String prefix = "BC-" + Year.now().getValue() + "-";
        long count = bonCommandeRepository.countByReferenceStartingWith(prefix);
        return String.format("%s%06d", prefix, count + 1);
    }

    @GetMapping
    public ResponseEntity<Page<BonCommande>> getBons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        Pageable pageable = PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "dateCreation"));
        if (search != null && !search.trim().isEmpty()) {
            return ResponseEntity.ok(
                    bonCommandeRepository
                            .findByReferenceContainingIgnoreCaseOrPatientNomContainingIgnoreCaseOrNumeroDossierContainingIgnoreCase(
                                    search, search, search, pageable));
        }
        return ResponseEntity.ok(bonCommandeRepository.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BonCommande> getBon(@PathVariable Long id) {
        return bonCommandeRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<BonCommande>> getBonsByPatient(@PathVariable Long patientId) {
        return ResponseEntity.ok(bonCommandeRepository.findByPatientIdOrderByDateCreationDesc(patientId));
    }

    @PostMapping
    public ResponseEntity<BonCommande> createBon(@RequestBody BonCommande bon) {
        Utilisateur user = getCurrentUser();
        if (user != null) {
            if (bon.getAgentId() == null) {
                bon.setAgentId(user.getId());
            }
            if (bon.getAgentNom() == null) {
                bon.setAgentNom(user.getPrenom() + " " + user.getNom());
            }
            if (bon.getBureauCsuId() == null) {
                bon.setBureauCsuId(user.getBureauId());
            }
        }
        if (bon.getDateCreation() == null) {
            bon.setDateCreation(LocalDateTime.now());
        }
        if (bon.getStatut() == null || bon.getStatut().isBlank()) {
            bon.setStatut("EN_ATTENTE");
        }
        if (bon.getReference() == null || bon.getReference().isBlank()) {
            bon.setReference(genererReference());
        }
        // La lettre de garantie correspond au dossier patient enregistré
        if ((bon.getReferenceLettreGarantie() == null || bon.getReferenceLettreGarantie().isBlank())
                && bon.getNumeroDossier() != null) {
            bon.setReferenceLettreGarantie(bon.getNumeroDossier());
        }
        if (bon.getMotif() == null || bon.getMotif().isBlank()) {
            bon.setMotif("Médicaments non disponibles à l'établissement de santé");
        }

        // Auto-fill patient data for official document
        if (bon.getPatientId() != null) {
            patientRepository.findById(bon.getPatientId()).ifPresent(patient -> {
                if (bon.getCodeAssureImmatriculation() == null || bon.getCodeAssureImmatriculation().isBlank()) {
                    String code = patient.getNumeroMatricule();
                    if (code == null || code.isBlank()) code = patient.getNumeroCni();
                    bon.setCodeAssureImmatriculation(code);
                }
                if (bon.getSexeBeneficiaire() == null || bon.getSexeBeneficiaire().isBlank()) {
                    bon.setSexeBeneficiaire(patient.getSexe());
                }
                if (bon.getAgeBeneficiaire() == null && patient.getDateNaissance() != null) {
                    bon.setAgeBeneficiaire(
                        java.time.Period.between(patient.getDateNaissance(), LocalDate.now()).getYears()
                    );
                }
                if (bon.getStructureSante() == null || bon.getStructureSante().isBlank()) {
                    bon.setStructureSante(patient.getService());
                }
            });
        }

        return ResponseEntity.ok(bonCommandeRepository.save(bon));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBon(@PathVariable Long id, @RequestBody BonCommande details) {
        Utilisateur user = getCurrentUser();
        return bonCommandeRepository.findById(id)
                .map(existing -> {
                    if (user != null && "AGENT".equals(user.getRole())
                            && existing.getAgentId() != null && !existing.getAgentId().equals(user.getId())) {
                        return ResponseEntity.status(403).body("Vous n'êtes pas autorisé à modifier ce bon car il appartient à un collègue.");
                    }
                    existing.setPatientId(details.getPatientId());
                    existing.setPatientNom(details.getPatientNom());
                    existing.setNumeroDossier(details.getNumeroDossier());
                    existing.setReferenceLettreGarantie(details.getReferenceLettreGarantie());
                    existing.setMedecinPrescripteur(details.getMedecinPrescripteur());
                    existing.setServiceHopital(details.getServiceHopital());
                    existing.setDateOrdonnance(details.getDateOrdonnance());
                    existing.setMotif(details.getMotif());
                    existing.setPharmacieId(details.getPharmacieId());
                    existing.setPharmacieNom(details.getPharmacieNom());
                    existing.setPharmacieAdresse(details.getPharmacieAdresse());
                    existing.setPharmacieTelephone(details.getPharmacieTelephone());
                    existing.setStatut(details.getStatut());
                    existing.setObservations(details.getObservations());
                    existing.setMontantEstime(details.getMontantEstime());
                    existing.setTypeCircuit(details.getTypeCircuit());
                    existing.setCodeAssureImmatriculation(details.getCodeAssureImmatriculation());
                    existing.setAgeBeneficiaire(details.getAgeBeneficiaire());
                    existing.setSexeBeneficiaire(details.getSexeBeneficiaire());
                    existing.setStructureSante(details.getStructureSante());
                    existing.setMontantPatient(details.getMontantPatient());
                    existing.setMontantTiersPayant(details.getMontantTiersPayant());
                    existing.setTauxPriseEnCharge(details.getTauxPriseEnCharge());
                    existing.setLignes(details.getLignes());
                    return ResponseEntity.ok(bonCommandeRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/statut")
    public ResponseEntity<?> changerStatut(@PathVariable Long id, @RequestParam String statut) {
        return bonCommandeRepository.findById(id)
                .map(existing -> {
                    existing.setStatut(statut);
                    return ResponseEntity.ok(bonCommandeRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBon(@PathVariable Long id) {
        Utilisateur user = getCurrentUser();
        return bonCommandeRepository.findById(id)
                .map(existing -> {
                    if (user != null && "AGENT".equals(user.getRole())
                            && existing.getAgentId() != null && !existing.getAgentId().equals(user.getId())) {
                        return ResponseEntity.status(403).body("Vous n'êtes pas autorisé à supprimer ce bon car il appartient à un collègue.");
                    }
                    bonCommandeRepository.deleteById(id);
                    return ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
