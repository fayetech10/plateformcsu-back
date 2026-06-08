package com.csu.gestion_csu.controller;

import com.csu.gestion_csu.model.Patient;
import com.csu.gestion_csu.service.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/patients")
@CrossOrigin(origins = "*") // For development
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;
    private final com.csu.gestion_csu.service.PatientExportService patientExportService;
    private final com.csu.gestion_csu.repository.PatientRepository patientRepository;
    private final com.csu.gestion_csu.repository.UtilisateurRepository utilisateurRepository;
    private final com.csu.gestion_csu.repository.BureauRepository bureauRepository;

    private Patient enrichir(Patient p) {
        if (p == null) return null;
        if (p.getAgentId() != null) {
            utilisateurRepository.findById(p.getAgentId())
                    .ifPresent(u -> p.setAgentNom(u.getPrenom() + " " + u.getNom()));
        }
        if (p.getBureauCsuId() != null) {
            bureauRepository.findById(p.getBureauCsuId())
                    .ifPresent(b -> p.setBureauCsuNom(b.getNom()));
        }
        return p;
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
    public ResponseEntity<Page<Patient>> getPatients(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        Page<Patient> result = patientService.getAllPatients(PageRequest.of(page, size, Sort.by("dateEnregistrement").descending()), search, getBureauIdFilter());
        result.getContent().forEach(this::enrichir);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<Patient>> searchPatients(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sexe,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String categorie,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Patient> result = patientService.searchPatients(getBureauIdFilter(), search, sexe, region, categorie, PageRequest.of(page, size, Sort.by("dateEnregistrement").descending()));
        result.getContent().forEach(this::enrichir);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/export/pdf")
    public void exportPdf(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sexe,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String categorie,
            jakarta.servlet.http.HttpServletResponse response) {
        java.util.List<Patient> patients = patientService.searchPatientsList(getBureauIdFilter(), search, sexe, region, categorie);
        patientExportService.exportPdf(patients, categorie, response);
    }

    @GetMapping("/export/excel")
    public void exportExcel(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sexe,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String categorie,
            jakarta.servlet.http.HttpServletResponse response) {
        java.util.List<Patient> patients = patientService.searchPatientsList(getBureauIdFilter(), search, sexe, region, categorie);
        patientExportService.exportExcel(patients, categorie, response);
    }

    @GetMapping("/stats")
    public ResponseEntity<java.util.Map<String, Object>> getStats() {
        java.util.List<Patient> patients = patientService.searchPatientsList(getBureauIdFilter(), null, null, null, null);

        java.util.Map<String, Long> parSexe = new java.util.LinkedHashMap<>();
        parSexe.put("Masculin", 0L);
        parSexe.put("Féminin", 0L);

        java.util.Map<String, Long> parCategorie = new java.util.LinkedHashMap<>();
        java.util.Map<String, Long> parCommune = new java.util.HashMap<>();

        java.util.Map<String, Long> parAge = new java.util.LinkedHashMap<>();
        parAge.put("0-5 ans", 0L);
        parAge.put("6-17 ans", 0L);
        parAge.put("18-35 ans", 0L);
        parAge.put("36-59 ans", 0L);
        parAge.put("60+ ans", 0L);

        java.time.LocalDate today = java.time.LocalDate.now();

        for (Patient p : patients) {
            // Sexe
            if ("M".equalsIgnoreCase(p.getSexe())) parSexe.merge("Masculin", 1L, Long::sum);
            else if ("F".equalsIgnoreCase(p.getSexe())) parSexe.merge("Féminin", 1L, Long::sum);

            // Catégorie
            String cat = com.csu.gestion_csu.service.PatientExportService.categorieLabel(p.getCategorie());
            parCategorie.merge(cat, 1L, Long::sum);

            // Commune
            if (p.getCommune() != null && !p.getCommune().isBlank()) {
                parCommune.merge(p.getCommune(), 1L, Long::sum);
            }

            // Tranche d'âge
            if (p.getDateNaissance() != null) {
                int age = java.time.Period.between(p.getDateNaissance(), today).getYears();
                if (age <= 5) parAge.merge("0-5 ans", 1L, Long::sum);
                else if (age <= 17) parAge.merge("6-17 ans", 1L, Long::sum);
                else if (age <= 35) parAge.merge("18-35 ans", 1L, Long::sum);
                else if (age <= 59) parAge.merge("36-59 ans", 1L, Long::sum);
                else parAge.merge("60+ ans", 1L, Long::sum);
            }
        }

        // Top 8 communes
        java.util.LinkedHashMap<String, Long> topCommunes = parCommune.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .collect(java.util.stream.Collectors.toMap(
                        java.util.Map.Entry::getKey, java.util.Map.Entry::getValue,
                        (a, b) -> a, java.util.LinkedHashMap::new));

        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("total", patients.size());
        stats.put("parSexe", parSexe);
        stats.put("parCategorie", parCategorie);
        stats.put("parCommune", topCommunes);
        stats.put("parAge", parAge);
        return ResponseEntity.ok(stats);
    }

    /**
     * Recherche un patient existant par identifiant (CNI en priorité, sinon matricule)
     * pour la déduplication : si le patient existe déjà, on lui délivre une lettre de
     * garantie au lieu de le réenregistrer. Renvoie 204 si aucun patient trouvé.
     */
    @GetMapping("/recherche-identite")
    public ResponseEntity<Patient> rechercheParIdentite(
            @RequestParam(required = false) String cni,
            @RequestParam(required = false) String matricule) {
        java.util.Optional<Patient> found = java.util.Optional.empty();
        if (cni != null && !cni.isBlank()) {
            found = patientRepository.findFirstByNumeroCniIgnoreCaseAndSupprimeFalse(cni.trim());
        }
        if (found.isEmpty() && matricule != null && !matricule.isBlank()) {
            found = patientRepository.findFirstByNumeroMatriculeIgnoreCaseAndSupprimeFalse(matricule.trim());
        }
        return found.map(this::enrichir).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Patient> getPatient(@PathVariable Long id) {
        return ResponseEntity.ok(enrichir(patientService.getPatientById(id)));
    }

    @PostMapping
    public ResponseEntity<Patient> createPatient(@RequestBody Patient patient) {
        com.csu.gestion_csu.model.Utilisateur user = getCurrentUser();
        Long agentId = user != null ? user.getId() : null;
        Long bureauId = user != null ? user.getBureauId() : null;
        return ResponseEntity.ok(enrichir(patientService.createPatient(patient, agentId, bureauId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updatePatient(@PathVariable Long id, @RequestBody Patient patient) {
        com.csu.gestion_csu.model.Utilisateur user = getCurrentUser();
        if (user != null && "AGENT".equals(user.getRole())) {
            Patient existing = patientService.getPatientById(id);
            if (!user.getId().equals(existing.getAgentId())) {
                return ResponseEntity.status(403).body("Vous n'êtes pas autorisé à modifier ce patient car il appartient à un collègue.");
            }
        }
        return ResponseEntity.ok(enrichir(patientService.updatePatient(id, patient)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePatient(@PathVariable Long id) {
        com.csu.gestion_csu.model.Utilisateur user = getCurrentUser();
        if (user != null && "AGENT".equals(user.getRole())) {
            Patient existing = patientService.getPatientById(id);
            if (!user.getId().equals(existing.getAgentId())) {
                return ResponseEntity.status(403).body("Vous n'êtes pas autorisé à supprimer ce patient car il appartient à un collègue.");
            }
        }
        patientService.deletePatient(id);
        return ResponseEntity.ok().build();
    }

}
