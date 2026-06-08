package com.csu.gestion_csu.controller;

import com.csu.gestion_csu.model.Activite;
import com.csu.gestion_csu.model.Enrolement;
import com.csu.gestion_csu.model.Patient;
import com.csu.gestion_csu.model.Utilisateur;
import com.csu.gestion_csu.repository.ActiviteRepository;
import com.csu.gestion_csu.repository.ConstatRepository;
import com.csu.gestion_csu.repository.EnrolementRepository;
import com.csu.gestion_csu.repository.PatientRepository;
import com.csu.gestion_csu.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DashboardController {

    private final PatientRepository patientRepository;
    private final EnrolementRepository enrolementRepository;
    private final ActiviteRepository activiteRepository;
    private final ConstatRepository constatRepository;
    private final UtilisateurRepository utilisateurRepository;

    /**
     * Returns the bureau ID of the currently authenticated user if they are an AGENT.
     * Returns null for ADMIN and SUPERVISEUR (they see global data).
     */
    private Long getCurrentUserBureauId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Utilisateur) {
            Utilisateur user = (Utilisateur) auth.getPrincipal();
            if ("AGENT".equals(user.getRole())) {
                return user.getBureauId();
            }
        }
        return null;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        Long bureauId = getCurrentUserBureauId();

        long totalPatients;
        long totalEnrolements;
        long totalActivites;
        long totalConstats;
        List<Activite> latestActivites;

        if (bureauId != null) {
            // Agent: filter by their bureau
            totalPatients = patientRepository.countByBureauCsuId(bureauId);
            totalEnrolements = enrolementRepository.countByBureauCsuId(bureauId);
            totalActivites = activiteRepository.countByBureauCsuId(bureauId);
            totalConstats = constatRepository.countByBureauCsuId(bureauId);
            latestActivites = activiteRepository.findByBureauCsuIdOrderByDateActiviteDesc(bureauId);
        } else {
            // Admin/Superviseur: global data
            totalPatients = patientRepository.count();
            totalEnrolements = enrolementRepository.count();
            totalActivites = activiteRepository.count();
            totalConstats = constatRepository.count();
            latestActivites = activiteRepository.findAll();
            latestActivites.sort((a, b) -> b.getDateActivite().compareTo(a.getDateActivite()));
        }

        stats.put("totalPatients", totalPatients);
        stats.put("totalBeneficiaires", totalEnrolements);
        stats.put("totalActivites", totalActivites);
        stats.put("totalConstats", totalConstats);

        // Fetch recent activities (limit 5)
        List<Map<String, Object>> activitesDuJour = latestActivites.stream()
                .limit(5)
                .map(act -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", act.getId());
                    map.put("typeActivite", act.getTypeActivite());
                    map.put("description", act.getDescription());
                    map.put("date", act.getDateActivite().format(DateTimeFormatter.ofPattern("HH:mm")));
                    // Resolve agent name
                    String agentName = "Agent CSU";
                    if (act.getAgentId() != null) {
                        agentName = utilisateurRepository.findById(act.getAgentId())
                                .map(u -> u.getPrenom() + " " + u.getNom())
                                .orElse("Agent CSU");
                    }
                    map.put("agent", agentName);
                    return map;
                })
                .collect(Collectors.toList());

        stats.put("activitesDuJour", activitesDuJour);

        // Monthly stats
        stats.put("statistiquesMensuelles", getMonthlyStatsMap(bureauId));

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/monthly-stats")
    public ResponseEntity<Map<String, Object>> getMonthlyStats() {
        Long bureauId = getCurrentUserBureauId();
        return ResponseEntity.ok(getMonthlyStatsMap(bureauId));
    }

    private Map<String, Object> getMonthlyStatsMap(Long bureauId) {
        String[] months = {"Janv", "Févr", "Mars", "Avril", "Mai", "Juin", "Juil", "Août", "Sept", "Oct", "Nov", "Déc"};
        
        List<Patient> patients;
        List<Enrolement> enrolements;

        if (bureauId != null) {
            patients = patientRepository.findByBureauCsuId(bureauId);
            enrolements = enrolementRepository.findAll().stream()
                    .filter(e -> bureauId.equals(e.getBureauCsuId()))
                    .collect(Collectors.toList());
        } else {
            patients = patientRepository.findAll();
            enrolements = enrolementRepository.findAll();
        }

        int[] patientsCounts = new int[12];
        int[] enrolementsCounts = new int[12];

        int currentYear = LocalDateTime.now().getYear();

        for (Patient p : patients) {
            LocalDateTime date = p.getDateEnregistrement();
            if (date != null && date.getYear() == currentYear) {
                int monthIdx = date.getMonthValue() - 1;
                if (monthIdx >= 0 && monthIdx < 12) {
                    patientsCounts[monthIdx]++;
                }
            }
        }

        for (Enrolement e : enrolements) {
            LocalDateTime date = e.getDateEnrolement();
            if (date != null && date.getYear() == currentYear) {
                int monthIdx = date.getMonthValue() - 1;
                if (monthIdx >= 0 && monthIdx < 12) {
                    enrolementsCounts[monthIdx]++;
                }
            }
        }

        List<Integer> patientsList = new ArrayList<>();
        List<Integer> enrolementsList = new ArrayList<>();

        for (int i = 0; i < 12; i++) {
            patientsList.add(patientsCounts[i]);
            enrolementsList.add(enrolementsCounts[i]);
        }

        Map<String, Object> monthlyStats = new HashMap<>();
        monthlyStats.put("labels", Arrays.asList(months));
        monthlyStats.put("patients", patientsList);
        monthlyStats.put("enrolements", enrolementsList);

        return monthlyStats;
    }
}
