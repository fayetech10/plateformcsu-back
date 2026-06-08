package com.csu.gestion_csu.controller;

import com.csu.gestion_csu.model.Bureau;
import com.csu.gestion_csu.model.LettreGarantie;
import com.csu.gestion_csu.model.Patient;
import com.csu.gestion_csu.model.Utilisateur;
import com.csu.gestion_csu.repository.BureauRepository;
import com.csu.gestion_csu.repository.LettreGarantieRepository;
import com.csu.gestion_csu.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Émission et consultation des lettres de garantie.
 * Règle métier : une lettre est valable {@link LettreGarantie#VALIDITE_JOURS} jours.
 * Tant qu'une lettre valide existe pour un patient, on ne réémet pas : on réutilise
 * la lettre déjà émise (cas du patient qui revient plusieurs fois dans la quinzaine).
 */
@RestController
@RequestMapping("/api/lettres-garantie")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LettreGarantieController {

    private final LettreGarantieRepository lettreRepository;
    private final PatientRepository patientRepository;
    private final BureauRepository bureauRepository;

    private Utilisateur getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Utilisateur) {
            return (Utilisateur) auth.getPrincipal();
        }
        return null;
    }

    private synchronized String genererReference() {
        String prefix = "LG-" + Year.now().getValue() + "-";
        long count = lettreRepository.countByReferenceStartingWith(prefix);
        return String.format("%s%06d", prefix, count + 1);
    }

    /** Liste toutes les lettres de garantie avec pagination (pour l'Admin) */
    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<LettreGarantie>> getLettres(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "dateEmission"));
        if (search != null && !search.trim().isEmpty()) {
            return ResponseEntity.ok(
                    lettreRepository.findByReferenceContainingIgnoreCaseOrPatientNomContainingIgnoreCase(
                            search, search, pageable));
        }
        return ResponseEntity.ok(lettreRepository.findAll(pageable));
    }

    /** Liste des lettres d'un patient (incluant celles partagées par CNI ou Matricule). */
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<LettreGarantie>> getByPatient(@PathVariable Long patientId) {
        Optional<Patient> patientOpt = patientRepository.findById(patientId);
        if (patientOpt.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        Patient p = patientOpt.get();
        
        List<LettreGarantie> direct = lettreRepository.findByPatientIdOrderByDateEmissionDesc(patientId);
        java.util.Set<Long> foundIds = new java.util.HashSet<>();
        direct.forEach(l -> foundIds.add(l.getId()));
        
        List<LettreGarantie> list = new java.util.ArrayList<>(direct);
        
        if (p.getNumeroCni() != null && !p.getNumeroCni().isBlank()) {
            List<LettreGarantie> parCni = lettreRepository.findByNumeroCniIgnoreCaseOrderByDateEmissionDesc(p.getNumeroCni().trim());
            for (LettreGarantie lg : parCni) {
                if (!foundIds.contains(lg.getId())) {
                    list.add(lg);
                    foundIds.add(lg.getId());
                }
            }
        }
        
        if (p.getNumeroMatricule() != null && !p.getNumeroMatricule().isBlank()) {
            List<Patient> sameMatriculePatients = patientRepository.findByNumeroMatriculeIgnoreCaseAndSupprimeFalse(p.getNumeroMatricule().trim());
            for (Patient other : sameMatriculePatients) {
                if (!other.getId().equals(patientId)) {
                    List<LettreGarantie> parAutre = lettreRepository.findByPatientIdOrderByDateEmissionDesc(other.getId());
                    for (LettreGarantie lg : parAutre) {
                        if (!foundIds.contains(lg.getId())) {
                            list.add(lg);
                            foundIds.add(lg.getId());
                        }
                    }
                }
            }
        }
        
        list.sort((a, b) -> b.getDateEmission().compareTo(a.getDateEmission()));
        return ResponseEntity.ok(list);
    }

    /** Lettre actuellement valide d'un patient — par patient, sinon par CNI (ou 204 si aucune). */
    @GetMapping("/patient/{patientId}/active")
    public ResponseEntity<LettreGarantie> getActive(@PathVariable Long patientId) {
        return trouverActive(patientId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /** Cherche une lettre valable : d'abord par patient, sinon par CNI du patient. */
    private Optional<LettreGarantie> trouverActive(Long patientId) {
        LocalDate today = LocalDate.now();
        Optional<LettreGarantie> parPatient = lettreRepository
                .findFirstByPatientIdAndDateExpirationGreaterThanEqualOrderByDateEmissionDesc(patientId, today);
        if (parPatient.isPresent()) return parPatient;

        Optional<Patient> pOpt = patientRepository.findById(patientId);
        if (pOpt.isPresent()) {
            Patient p = pOpt.get();
            if (p.getNumeroCni() != null && !p.getNumeroCni().isBlank()) {
                Optional<LettreGarantie> parCni = lettreRepository
                        .findFirstByNumeroCniIgnoreCaseAndDateExpirationGreaterThanEqualOrderByDateEmissionDesc(p.getNumeroCni().trim(), today);
                if (parCni.isPresent()) return parCni;
            }
            if (p.getNumeroMatricule() != null && !p.getNumeroMatricule().isBlank()) {
                List<Patient> sameMatriculePatients = patientRepository.findByNumeroMatriculeIgnoreCaseAndSupprimeFalse(p.getNumeroMatricule().trim());
                for (Patient other : sameMatriculePatients) {
                    if (!other.getId().equals(patientId)) {
                        Optional<LettreGarantie> parMatricule = lettreRepository
                                .findFirstByPatientIdAndDateExpirationGreaterThanEqualOrderByDateEmissionDesc(other.getId(), today);
                        if (parMatricule.isPresent()) return parMatricule;
                    }
                }
            }
        }

        return Optional.empty();
    }

    @GetMapping("/{id}")
    public ResponseEntity<LettreGarantie> getOne(@PathVariable Long id) {
        return lettreRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Émet une lettre de garantie pour un patient — ou réutilise la lettre encore
     * valide si elle existe. Réponse : { reused: boolean, lettre: {...} }.
     */
    @PostMapping("/emettre")
    public ResponseEntity<Map<String, Object>> emettre(@RequestParam Long patientId) {
        Optional<Patient> patientOpt = patientRepository.findById(patientId);
        if (patientOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Patient introuvable."));
        }
        Patient p = patientOpt.get();

        // Réutilisation si une lettre est encore valable (par patient, sinon par CNI)
        Optional<LettreGarantie> active = trouverActive(patientId);
        if (active.isPresent()) {
            Map<String, Object> body = new HashMap<>();
            body.put("reused", true);
            body.put("lettre", active.get());
            body.put("message", "Une lettre de garantie est déjà valable pour ce patient jusqu'au "
                    + active.get().getDateExpiration() + ". Réutilisez cette lettre.");
            return ResponseEntity.ok(body);
        }

        Utilisateur user = getCurrentUser();

        // Résoudre le nom de la structure via la structure de l'agent
        String structureNom = null;
        if (user != null && user.getStructureId() != null) {
            structureNom = bureauRepository.findById(user.getStructureId())
                    .map(Bureau::getNom)
                    .orElse(null);
        } else if (p.getBureauCsuId() != null) {
            // Fallback sur le bureau du patient si l'agent n'a pas de structure
            structureNom = bureauRepository.findById(p.getBureauCsuId())
                    .map(Bureau::getNom)
                    .orElse(null);
        }

        // Déterminer le code assuré/immatriculation
        String codeAssure = p.getNumeroMatricule();
        if (codeAssure == null || codeAssure.isBlank()) {
            codeAssure = p.getNumeroCni();
        }

        // Calculer l'âge
        Integer age = null;
        if (p.getDateNaissance() != null) {
            age = java.time.Period.between(p.getDateNaissance(), LocalDate.now()).getYears();
        }

        // Sinon, nouvelle émission
        LocalDateTime now = LocalDateTime.now();
        LettreGarantie lettre = LettreGarantie.builder()
                .reference(genererReference())
                .patientId(p.getId())
                .patientNom((p.getPrenom() + " " + p.getNom()).trim())
                .numeroDossier(p.getNumeroDossier())
                .numeroCni(p.getNumeroCni())
                .categorie(p.getCategorie())
                .ageBeneficiaire(age)
                .sexeBeneficiaire(p.getSexe())
                .structure(structureNom)
                .typeAssure(p.getCategorie())
                .codeAssureImmatriculation(codeAssure)
                .motif(p.getDiagnosticMotif())
                .tauxPriseEnCharge("80%")  // Forcé à 80% comme demandé
                .dateEmission(now)
                .dateExpiration(now.toLocalDate().plusDays(LettreGarantie.VALIDITE_JOURS))
                .agentId(user == null ? null : user.getId())
                .agentNom(user == null ? null : (user.getPrenom() + " " + user.getNom()))
                .bureauCsuId(p.getBureauCsuId())
                .build();
        lettre = lettreRepository.save(lettre);

        Map<String, Object> body = new HashMap<>();
        body.put("reused", false);
        body.put("lettre", lettre);
        body.put("message", "Lettre de garantie émise, valable jusqu'au " + lettre.getDateExpiration() + ".");
        return ResponseEntity.ok(body);
    }
}
