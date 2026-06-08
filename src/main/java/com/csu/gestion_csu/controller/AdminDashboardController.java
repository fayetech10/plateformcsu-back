package com.csu.gestion_csu.controller;

import com.csu.gestion_csu.model.Activite;
import com.csu.gestion_csu.model.Bureau;
import com.csu.gestion_csu.model.Constat;
import com.csu.gestion_csu.model.Enrolement;
import com.csu.gestion_csu.model.Patient;
import com.csu.gestion_csu.model.Utilisateur;
import com.csu.gestion_csu.repository.ActiviteRepository;
import com.csu.gestion_csu.repository.BureauRepository;
import com.csu.gestion_csu.repository.CategorieRepository;
import com.csu.gestion_csu.repository.ConstatRepository;
import com.csu.gestion_csu.repository.EnrolementRepository;
import com.csu.gestion_csu.repository.PatientRepository;
import com.csu.gestion_csu.repository.PointageRepository;
import com.csu.gestion_csu.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Statistiques dédiées à l'administration de la plateforme.
 * Réservé au rôle ADMIN : vue d'ensemble des utilisateurs, bureaux,
 * catégories et de la charge par bureau.
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminDashboardController {

    private final UtilisateurRepository utilisateurRepository;
    private final BureauRepository bureauRepository;
    private final CategorieRepository categorieRepository;
    private final PatientRepository patientRepository;
    private final EnrolementRepository enrolementRepository;
    private final ActiviteRepository activiteRepository;
    private final ConstatRepository constatRepository;
    private final PointageRepository pointageRepository;

    /** Heure limite d'arrivée : au-delà, l'agent est considéré en retard. */
    private static final java.time.LocalTime HEURE_LIMITE = java.time.LocalTime.of(8, 0);

    /** Convertit une List<Object[]> [clé, count] en Map<clé, Long>. */
    private static <K> Map<K, Long> toCountMap(List<Object[]> rows) {
        Map<K, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] == null) continue;
            @SuppressWarnings("unchecked") K k = (K) row[0];
            map.put(k, ((Number) row[1]).longValue());
        }
        return map;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getAdminStats() {
        Map<String, Object> stats = new HashMap<>();

        List<Utilisateur> utilisateurs = utilisateurRepository.findAll();
        List<Bureau> bureaux = bureauRepository.findAll();

        // ── Utilisateurs ──────────────────────────────────────────────
        long totalUtilisateurs = utilisateurs.size();
        long utilisateursActifs = utilisateurs.stream().filter(Utilisateur::isActif).count();
        stats.put("totalUtilisateurs", totalUtilisateurs);
        stats.put("utilisateursActifs", utilisateursActifs);
        stats.put("utilisateursInactifs", totalUtilisateurs - utilisateursActifs);

        // Répartition par rôle (ordre fixe pour un affichage stable)
        Map<String, Long> repartitionRoles = new LinkedHashMap<>();
        repartitionRoles.put("ADMIN", 0L);
        repartitionRoles.put("SUPERVISEUR", 0L);
        repartitionRoles.put("AGENT", 0L);
        for (Utilisateur u : utilisateurs) {
            String role = u.getRole() == null ? "AGENT" : u.getRole();
            repartitionRoles.merge(role, 1L, Long::sum);
        }
        stats.put("repartitionRoles", repartitionRoles);

        // ── Bureaux ──────────────────────────────────────────────────
        long bureauxActifs = bureaux.stream().filter(Bureau::isActif).count();
        stats.put("totalBureaux", (long) bureaux.size());
        stats.put("bureauxActifs", bureauxActifs);
        stats.put("bureauxInactifs", bureaux.size() - bureauxActifs);

        // ── Catégories ───────────────────────────────────────────────
        stats.put("totalCategories", categorieRepository.count());

        // ── Totaux système global ────────────────────────────────────
        stats.put("totalPatients", patientRepository.count());
        stats.put("totalEnrolements", enrolementRepository.count());
        stats.put("totalActivites", activiteRepository.count());
        stats.put("totalConstats", constatRepository.count());

        // Map bureauId -> nom (pour résoudre le bureau des utilisateurs)
        Map<Long, String> bureauNomById = bureaux.stream()
                .collect(Collectors.toMap(Bureau::getId, Bureau::getNom, (a, b) -> a));

        // ── Derniers utilisateurs créés (id décroissant ~ ordre de création) ──
        List<Map<String, Object>> derniersUtilisateurs = utilisateurs.stream()
                .sorted(Comparator.comparing(Utilisateur::getId).reversed())
                .limit(6)
                .map(u -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", u.getId());
                    map.put("nom", u.getNom());
                    map.put("prenom", u.getPrenom());
                    map.put("role", u.getRole());
                    map.put("email", u.getEmail());
                    map.put("actif", u.isActif());
                    map.put("bureauNom", u.getBureauId() == null
                            ? "—" : bureauNomById.getOrDefault(u.getBureauId(), "—"));
                    return map;
                })
                .collect(Collectors.toList());
        stats.put("derniersUtilisateurs", derniersUtilisateurs);

        // ── Charge par bureau ────────────────────────────────────────
        // Agents groupés par bureau (pour afficher noms + nombre)
        Map<Long, List<Utilisateur>> agentsParBureau = utilisateurs.stream()
                .filter(u -> u.getBureauId() != null)
                .collect(Collectors.groupingBy(Utilisateur::getBureauId));

        // Counts groupés en 4 requêtes SQL (GROUP BY) au lieu de 4 × N requêtes.
        Map<Long, Long> patientsParBureau = toCountMap(patientRepository.countByBureauGrouped());
        Map<Long, Long> enrolementsParBureau = toCountMap(enrolementRepository.countByBureauGrouped());
        Map<Long, Long> activitesParBureau = toCountMap(activiteRepository.countByBureauGrouped());
        Map<Long, Long> constatsParBureau = toCountMap(constatRepository.countByBureauGrouped());

        List<Map<String, Object>> bureauxStats = bureaux.stream()
                .map(b -> {
                    Map<String, Object> map = new HashMap<>();
                    long patients = patientsParBureau.getOrDefault(b.getId(), 0L);
                    long enrolements = enrolementsParBureau.getOrDefault(b.getId(), 0L);
                    long activites = activitesParBureau.getOrDefault(b.getId(), 0L);
                    long constats = constatsParBureau.getOrDefault(b.getId(), 0L);
                    List<Utilisateur> agents = agentsParBureau.getOrDefault(b.getId(), Collections.emptyList());
                    map.put("id", b.getId());
                    map.put("nom", b.getNom());
                    map.put("region", b.getRegion());
                    map.put("type", b.getType());
                    map.put("actif", b.isActif());
                    map.put("agents", (long) agents.size());
                    map.put("agentsNoms", agents.stream()
                            .map(u -> u.getPrenom() + " " + u.getNom())
                            .collect(Collectors.toList()));
                    map.put("patients", patients);
                    map.put("enrolements", enrolements);
                    map.put("activites", activites);
                    map.put("constats", constats);
                    return map;
                })
                .sorted((a, b) -> Long.compare(
                        (Long) b.get("patients"), (Long) a.get("patients")))
                .collect(Collectors.toList());
        stats.put("bureauxStats", bureauxStats);

        return ResponseEntity.ok(stats);
    }

    /**
     * Statistiques de performance par agent : pour chaque agent, le nombre de
     * patients enregistrés, d'enrôlements, d'activités et de constats traités,
     * ainsi que des indicateurs synthétiques (moyennes, agent le plus actif).
     */
    @GetMapping("/stats-agents")
    public ResponseEntity<Map<String, Object>> getStatsAgents() {
        List<Utilisateur> utilisateurs = utilisateurRepository.findAll();
        Map<Long, String> bureauNomById = bureauRepository.findAll().stream()
                .collect(Collectors.toMap(Bureau::getId, Bureau::getNom, (a, b) -> a));

        // Agrégats par agent : effectués directement en SQL via GROUP BY (4 requêtes au lieu de findAll().stream())
        Map<Long, Long> patientsParAgent = toCountMap(patientRepository.countByAgentGrouped());
        Map<Long, Long> enrolementsParAgent = toCountMap(enrolementRepository.countByAgentGrouped());
        Map<Long, Long> activitesParAgent = toCountMap(activiteRepository.countByAgentGrouped());
        Map<Long, Long> constatsParAgent = toCountMap(constatRepository.countByResponsableGrouped());

        List<Utilisateur> agents = utilisateurs.stream()
                .filter(u -> "AGENT".equals(u.getRole()))
                .collect(Collectors.toList());

        List<Map<String, Object>> lignes = agents.stream().map(u -> {
            long p = patientsParAgent.getOrDefault(u.getId(), 0L);
            long e = enrolementsParAgent.getOrDefault(u.getId(), 0L);
            long a = activitesParAgent.getOrDefault(u.getId(), 0L);
            long c = constatsParAgent.getOrDefault(u.getId(), 0L);
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("nom", u.getNom());
            m.put("prenom", u.getPrenom());
            m.put("actif", u.isActif());
            m.put("bureauNom", u.getBureauId() == null ? "—" : bureauNomById.getOrDefault(u.getBureauId(), "—"));
            m.put("patients", p);
            m.put("enrolements", e);
            m.put("activites", a);
            m.put("constats", c);
            m.put("total", p + e + a + c);
            return m;
        }).sorted((x, y) -> Long.compare((Long) y.get("total"), (Long) x.get("total")))
                .collect(Collectors.toList());

        int nbAgents = agents.size();
        long totalPatients = lignes.stream().mapToLong(m -> (Long) m.get("patients")).sum();
        long totalEnrolements = lignes.stream().mapToLong(m -> (Long) m.get("enrolements")).sum();
        long totalActivites = lignes.stream().mapToLong(m -> (Long) m.get("activites")).sum();
        long totalConstats = lignes.stream().mapToLong(m -> (Long) m.get("constats")).sum();

        Map<String, Object> result = new HashMap<>();
        result.put("agents", lignes);
        result.put("nbAgents", nbAgents);
        result.put("moyennePatientsParAgent", nbAgents == 0 ? 0 : Math.round((double) totalPatients / nbAgents * 10) / 10.0);
        result.put("moyenneEnrolementsParAgent", nbAgents == 0 ? 0 : Math.round((double) totalEnrolements / nbAgents * 10) / 10.0);
        result.put("moyenneActivitesParAgent", nbAgents == 0 ? 0 : Math.round((double) totalActivites / nbAgents * 10) / 10.0);
        result.put("totalPatients", totalPatients);
        result.put("totalEnrolements", totalEnrolements);
        result.put("totalActivites", totalActivites);
        result.put("totalConstats", totalConstats);
        result.put("agentTop", lignes.isEmpty() ? null : (lignes.get(0).get("prenom") + " " + lignes.get(0).get("nom")));
        // Nombre d'agents sans aucune contribution (inactivité opérationnelle)
        result.put("agentsSansActivite", lignes.stream().filter(m -> (Long) m.get("total") == 0L).count());
        return ResponseEntity.ok(result);
    }

    /**
     * Statistiques géographiques et temporelles des patients :
     * répartition par région, département, commune et par année d'enregistrement.
     */
    @GetMapping("/stats-geo")
    public ResponseEntity<Map<String, Object>> getStatsGeo() {
        // 4 requêtes GROUP BY en base — pas de chargement complet des patients en mémoire.
        Map<String, Long> parRegion = trieDecroissant(toStringCountMap(patientRepository.countByRegionGrouped()));
        Map<String, Long> parDepartement = trieDecroissant(toStringCountMap(patientRepository.countByDepartementGrouped()));
        Map<String, Long> parCommune = trieDecroissant(toStringCountMap(patientRepository.countByCommuneGrouped()));

        // Année : ordre chronologique (TreeMap)
        Map<String, Long> parAnnee = new TreeMap<>();
        for (Object[] row : patientRepository.countByAnneeGrouped()) {
            String annee = row[0] == null ? "Non renseignée" : String.valueOf(row[0]);
            parAnnee.merge(annee, ((Number) row[1]).longValue(), Long::sum);
        }

        long total = parRegion.values().stream().mapToLong(Long::longValue).sum();

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("parRegion", parRegion);
        result.put("parDepartement", parDepartement);
        result.put("parCommune", parCommune);
        result.put("parAnnee", parAnnee);
        return ResponseEntity.ok(result);
    }

    /** Convertit une List<Object[]> [String, count] en Map, normalisant les valeurs nulles/vides. */
    private static Map<String, Long> toStringCountMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            String k = (row[0] == null || ((String) row[0]).isBlank()) ? "Non renseigné" : (String) row[0];
            map.merge(k, ((Number) row[1]).longValue(), Long::sum);
        }
        return map;
    }

    /** Réordonne par effectif décroissant. */
    private static Map<String, Long> trieDecroissant(Map<String, Long> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * Ponctualité des agents : nombre d'arrivées à l'heure et en retard
     * (par rapport à l'heure limite), au global et par agent.
     */
    @GetMapping("/stats-ponctualite")
    public ResponseEntity<Map<String, Object>> getStatsPonctualite() {
        // Agrégat SQL : 1 requête GROUP BY au lieu de charger tous les pointages en mémoire.
        int limiteMinutes = HEURE_LIMITE.getHour() * 60 + HEURE_LIMITE.getMinute();
        List<Object[]> rows = pointageRepository.countPonctualiteByAgent(limiteMinutes);

        // Référentiels résolus en un seul appel chacun.
        Map<Long, String> nomById = utilisateurRepository.findAll().stream()
                .collect(Collectors.toMap(Utilisateur::getId,
                        u -> u.getPrenom() + " " + u.getNom(), (a, b) -> a));
        Map<Long, String> bureauNomById = bureauRepository.findAll().stream()
                .collect(Collectors.toMap(Bureau::getId, Bureau::getNom, (a, b) -> a));
        Map<Long, Long> bureauByAgent = utilisateurRepository.findAll().stream()
                .filter(u -> u.getBureauId() != null)
                .collect(Collectors.toMap(Utilisateur::getId, Utilisateur::getBureauId, (a, b) -> a));

        long total = 0, aLHeure = 0, enRetard = 0;
        List<Map<String, Object>> agents = new java.util.ArrayList<>();
        for (Object[] row : rows) {
            Long agentId = (Long) row[0];
            long a = row[1] == null ? 0 : ((Number) row[1]).longValue();
            long r = row[2] == null ? 0 : ((Number) row[2]).longValue();
            aLHeure += a; enRetard += r; total += (a + r);
            Map<String, Object> m = new HashMap<>();
            m.put("agentId", agentId);
            m.put("nom", nomById.getOrDefault(agentId, "Agent #" + agentId));
            Long bId = bureauByAgent.get(agentId);
            m.put("bureauNom", bId == null ? "—" : bureauNomById.getOrDefault(bId, "—"));
            m.put("aLHeure", a);
            m.put("enRetard", r);
            m.put("total", a + r);
            m.put("tauxPonctualite", (a + r) == 0 ? 0 : Math.round((double) a / (a + r) * 100));
            agents.add(m);
        }
        agents.sort((x, y) -> Long.compare((Long) y.get("enRetard"), (Long) x.get("enRetard")));

        Map<String, Object> result = new HashMap<>();
        result.put("heureLimite", HEURE_LIMITE.toString());
        result.put("totalArrivees", total);
        result.put("aLHeure", aLHeure);
        result.put("enRetard", enRetard);
        result.put("tauxPonctualite", total == 0 ? 0 : Math.round((double) aLHeure / total * 100));
        result.put("agents", agents);
        return ResponseEntity.ok(result);
    }

    /**
     * Cartographie des bureaux : coordonnées + noms des agents rattachés,
     * pour un affichage sur carte (markers cliquables).
     */
    @GetMapping("/bureaux-carte")
    public ResponseEntity<List<Map<String, Object>>> getBureauxCarte() {
        List<Utilisateur> utilisateurs = utilisateurRepository.findAll();
        Map<Long, List<Utilisateur>> agentsParBureau = utilisateurs.stream()
                .filter(u -> u.getBureauId() != null)
                .collect(Collectors.groupingBy(Utilisateur::getBureauId));

        List<Map<String, Object>> bureaux = bureauRepository.findAll().stream()
                .filter(b -> b.getLatitude() != null && b.getLongitude() != null)
                .map(b -> {
                    List<Utilisateur> agents = agentsParBureau.getOrDefault(b.getId(), Collections.emptyList());
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", b.getId());
                    m.put("nom", b.getNom());
                    m.put("region", b.getRegion());
                    m.put("commune", b.getCommune());
                    m.put("type", b.getType());
                    m.put("actif", b.isActif());
                    m.put("latitude", b.getLatitude());
                    m.put("longitude", b.getLongitude());
                    m.put("nbAgents", agents.size());
                    m.put("agents", agents.stream()
                            .map(u -> u.getPrenom() + " " + u.getNom())
                            .collect(Collectors.toList()));
                    m.put("patients", patientRepository.countByBureauCsuId(b.getId()));
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(bureaux);
    }

    /**
     * Détail complet d'un bureau : informations, agents et toutes les
     * données rattachées (patients, activités, enrôlements, constats).
     */
    @GetMapping("/bureaux/{id}")
    public ResponseEntity<Map<String, Object>> getBureauDetail(@PathVariable Long id) {
        Optional<Bureau> bureauOpt = bureauRepository.findById(id);
        if (bureauOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Bureau b = bureauOpt.get();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        DateTimeFormatter dfDay = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        // Résolution des noms d'agents (pour patients/activités/...)
        List<Utilisateur> agents = utilisateurRepository.findByBureauId(id);
        Map<Long, String> agentNomById = utilisateurRepository.findAll().stream()
                .collect(Collectors.toMap(Utilisateur::getId,
                        u -> u.getPrenom() + " " + u.getNom(), (x, y) -> x));

        Map<String, Object> result = new HashMap<>();

        // ── Informations du bureau ──
        Map<String, Object> info = new HashMap<>();
        info.put("id", b.getId());
        info.put("nom", b.getNom());
        info.put("code", b.getCode());
        info.put("region", b.getRegion());
        info.put("departement", b.getDepartement());
        info.put("commune", b.getCommune());
        info.put("adresse", b.getAdresse());
        info.put("telephone", b.getTelephone());
        info.put("type", b.getType());
        info.put("actif", b.isActif());
        result.put("bureau", info);

        // ── Compteurs ──
        Map<String, Object> stats = new HashMap<>();
        stats.put("agents", agents.size());
        stats.put("patients", patientRepository.countByBureauCsuId(id));
        stats.put("enrolements", enrolementRepository.countByBureauCsuId(id));
        stats.put("activites", activiteRepository.countByBureauCsuId(id));
        stats.put("constats", constatRepository.countByBureauCsuId(id));
        result.put("stats", stats);

        // ── Agents ──
        result.put("agents", agents.stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("nom", u.getNom());
            m.put("prenom", u.getPrenom());
            m.put("role", u.getRole());
            m.put("email", u.getEmail());
            m.put("telephone", u.getTelephone());
            m.put("actif", u.isActif());
            return m;
        }).collect(Collectors.toList()));

        // ── Patients ──
        List<Patient> patients = patientRepository.findByBureauCsuId(id);
        result.put("patients", patients.stream()
                .sorted(Comparator.comparing(Patient::getDateEnregistrement,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", p.getId());
                    m.put("numeroDossier", p.getNumeroDossier());
                    m.put("nom", p.getNom());
                    m.put("prenom", p.getPrenom());
                    m.put("sexe", p.getSexe());
                    m.put("categorie", p.getCategorie());
                    m.put("telephone", p.getTelephone());
                    m.put("commune", p.getCommune());
                    m.put("agent", p.getAgentId() == null ? "—" : agentNomById.getOrDefault(p.getAgentId(), "—"));
                    m.put("date", p.getDateEnregistrement() == null ? "—" : p.getDateEnregistrement().format(df));
                    return m;
                }).collect(Collectors.toList()));

        // ── Activités ──
        List<Activite> activites = activiteRepository.findByBureauCsuIdOrderByDateActiviteDesc(id);
        result.put("activites", activites.stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("typeActivite", a.getTypeActivite());
            m.put("description", a.getDescription());
            m.put("nombreParticipants", a.getNombreParticipants());
            m.put("agent", a.getAgentId() == null ? "—" : agentNomById.getOrDefault(a.getAgentId(), "—"));
            m.put("date", a.getDateActivite() == null ? "—" : a.getDateActivite().format(df));
            return m;
        }).collect(Collectors.toList()));

        // ── Enrôlements ──
        List<Enrolement> enrolements = enrolementRepository.findByBureauCsuIdOrderByDateEnrolementDesc(id);
        result.put("enrolements", enrolements.stream().map(e -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", e.getId());
            m.put("numeroBeneficiaire", e.getNumeroBeneficiaire());
            String patientNom = "—";
            if (e.getPrenom() != null || e.getNom() != null) {
                patientNom = ((e.getPrenom() == null ? "" : e.getPrenom()) + " " + (e.getNom() == null ? "" : e.getNom())).trim();
            } else if (e.getPatient() != null) {
                patientNom = e.getPatient().getPrenom() + " " + e.getPatient().getNom();
            }
            m.put("patient", patientNom);
            m.put("statut", e.getStatut());
            m.put("agent", e.getAgentId() == null ? "—" : agentNomById.getOrDefault(e.getAgentId(), "—"));
            m.put("date", e.getDateEnrolement() == null ? "—" : e.getDateEnrolement().format(df));
            return m;
        }).collect(Collectors.toList()));

        // ── Constats ──
        List<Constat> constats = constatRepository.findByBureauCsuIdOrderByDateConstatDesc(id);
        result.put("constats", constats.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("referenceConstat", c.getReferenceConstat());
            m.put("description", c.getDescription());
            m.put("priorite", c.getPriorite());
            m.put("statut", c.getStatut());
            m.put("responsable", c.getResponsableId() == null ? "—" : agentNomById.getOrDefault(c.getResponsableId(), "—"));
            m.put("date", c.getDateConstat() == null ? "—" : c.getDateConstat().format(dfDay));
            return m;
        }).collect(Collectors.toList()));

        return ResponseEntity.ok(result);
    }
}
