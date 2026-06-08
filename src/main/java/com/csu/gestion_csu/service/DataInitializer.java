package com.csu.gestion_csu.service;

import com.csu.gestion_csu.model.*;
import com.csu.gestion_csu.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UtilisateurRepository utilisateurRepository;
    private final BureauRepository bureauRepository;
    private final CategorieRepository categorieRepository;
    private final PatientRepository patientRepository;
    private final EnrolementRepository enrolementRepository;
    private final ActiviteRepository activiteRepository;
    private final ConstatRepository constatRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    /** Exécute des instructions SQL de migration en ignorant les erreurs (idempotent / best-effort). */
    private void execIgnore(String... sqls) {
        for (String sql : sqls) {
            try { jdbcTemplate.execute(sql); return; } catch (Exception ignored) { /* essaie la suivante */ }
        }
    }

    @Override
    public void run(String... args) throws Exception {
        // Migration : l'enrôlement n'est plus rattaché à un patient -> patient_id devient nullable.
        // ddl-auto=update ne relâche pas une contrainte NOT NULL existante, on le fait manuellement.
        execIgnore(
                "ALTER TABLE ENROLEMENTS ALTER COLUMN PATIENT_ID DROP NOT NULL",
                "ALTER TABLE ENROLEMENTS ALTER COLUMN PATIENT_ID SET NULL"
        );

        // Index de performance — créés sur la base existante si Hibernate ne l'a pas fait (CREATE INDEX IF NOT EXISTS, H2).
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_PATIENT_BUREAU ON PATIENTS(BUREAU_CSU_ID)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_PATIENT_AGENT ON PATIENTS(AGENT_ID)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_PATIENT_CATEGORIE ON PATIENTS(CATEGORIE)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_PATIENT_SUPPRIME ON PATIENTS(SUPPRIME)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_PATIENT_DATE_ENR ON PATIENTS(DATE_ENREGISTREMENT)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_ENROL_BUREAU ON ENROLEMENTS(BUREAU_CSU_ID)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_ENROL_AGENT ON ENROLEMENTS(AGENT_ID)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_ENROL_STATUT ON ENROLEMENTS(STATUT)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_ENROL_PATIENT ON ENROLEMENTS(PATIENT_ID)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_ACTIVITE_BUREAU ON ACTIVITES(BUREAU_CSU_ID)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_ACTIVITE_AGENT ON ACTIVITES(AGENT_ID)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_ACTIVITE_DATE ON ACTIVITES(DATE_ACTIVITE)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_CONSTAT_BUREAU ON CONSTATS(BUREAU_CSU_ID)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_CONSTAT_RESP ON CONSTATS(RESPONSABLE_ID)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_CONSTAT_STATUT ON CONSTATS(STATUT)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_CONSTAT_DATE ON CONSTATS(DATE_CONSTAT)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_POINTAGE_AGENT_DATE ON POINTAGES(AGENT_ID, DATE_POINTAGE)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_POINTAGE_DATE ON POINTAGES(DATE_POINTAGE)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_POINTAGE_BUREAU ON POINTAGES(BUREAU_ID)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_USER_BUREAU ON UTILISATEURS(BUREAU_ID)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_USER_ROLE ON UTILISATEURS(ROLE)");
        execIgnore("CREATE INDEX IF NOT EXISTS IDX_USER_ACTIF ON UTILISATEURS(ACTIF)");

        // Backfill : renseigne une date de création pour les comptes existants qui n'en ont pas
        java.util.List<Utilisateur> sansDate = utilisateurRepository.findAll().stream()
                .filter(u -> u.getDateCreation() == null)
                .toList();
        if (!sansDate.isEmpty()) {
            sansDate.forEach(u -> u.setDateCreation(LocalDateTime.now()));
            utilisateurRepository.saveAll(sansDate);
        }

        if (utilisateurRepository.count() > 0) {
            return; // Already initialized
        }

        // 1. Seed Bureaux
        Bureau bureauCentral = Bureau.builder()
                .nom("Bureau Central de Dakar")
                .code("BC-DKR")
                .region("Dakar")
                .departement("Dakar")
                .commune("Plateau")
                .adresse("Avenue Cheikh Anta Diop")
                .telephone("+221338000000")
                .type("CSU")
                .actif(true)
                .build();
        bureauCentral = bureauRepository.save(bureauCentral);

        Bureau structureA = Bureau.builder()
                .nom("Centre de Sante Gaspard Kamara")
                .code("CS-GSP")
                .region("Dakar")
                .departement("Dakar")
                .commune("Mermoz")
                .adresse("Rue de Mermoz")
                .telephone("+221338240000")
                .type("Structure de Sante")
                .actif(true)
                .build();
        structureA = bureauRepository.save(structureA);

        // 2. Seed Categories
        Categorie catSensibilisation = Categorie.builder()
                .nom("Sensibilisation")
                .description("Sensibilisation itinérante sur les marchés hebdomadaires")
                .type("ACTIVITE")
                .actif(true)
                .build();
        catSensibilisation = categorieRepository.save(catSensibilisation);

        Categorie catAtelier = Categorie.builder()
                .nom("Ateliers Techniques")
                .description("Atelier de travail avec les prestataires de santé")
                .type("ACTIVITE")
                .actif(true)
                .build();
        catAtelier = categorieRepository.save(catAtelier);

        Categorie catMateriel = Categorie.builder()
                .nom("Materiel")
                .description("Imprimantes, ordinateurs, terminaux biométriques")
                .type("CONSTAT")
                .actif(true)
                .build();
        catMateriel = categorieRepository.save(catMateriel);

        Categorie catLogiciel = Categorie.builder()
                .nom("Logiciel")
                .description("Erreurs système, lenteurs base de données, bugs")
                .type("CONSTAT")
                .actif(true)
                .build();
        catLogiciel = categorieRepository.save(catLogiciel);

        // 3. Seed Users
        Utilisateur admin = Utilisateur.builder()
                .prenom("System")
                .nom("Administrator")
                .username("admin")
                .password(passwordEncoder.encode("admin"))
                .email("admin@csu.sn")
                .telephone("775556677")
                .role("ADMIN")
                .actif(true)
                .dateCreation(LocalDateTime.now())
                .build();
        utilisateurRepository.save(admin);

        Utilisateur agent = Utilisateur.builder()
                .prenom("John")
                .nom("Doe")
                .username("agent")
                .password(passwordEncoder.encode("agent"))
                .email("agent@csu.sn")
                .telephone("782223344")
                .role("AGENT")
                .bureauId(bureauCentral.getId())
                .actif(true)
                .dateCreation(LocalDateTime.now())
                .build();
        agent = utilisateurRepository.save(agent);

        // 4. Seed Patients
        Patient patient1 = Patient.builder()
                .numeroDossier("DOS-001")
                .categorie("classique")
                .prenom("Amina")
                .nom("Diop")
                .sexe("F")
                .dateNaissance(LocalDate.of(1995, 5, 12))
                .telephone("+221771234567")
                .adresse("Grand Yoff")
                .region("Dakar")
                .departement("Dakar")
                .commune("Grand Yoff")
                .dateEnregistrement(LocalDateTime.now().minusMonths(2))
                .agentId(agent.getId())
                .bureauCsuId(bureauCentral.getId())
                .supprime(false)
                .build();
        patient1 = patientRepository.save(patient1);

        Patient patient2 = Patient.builder()
                .numeroDossier("DOS-002")
                .categorie("0-5ans")
                .prenom("Moussa")
                .nom("Ndiaye")
                .sexe("M")
                .dateNaissance(LocalDate.of(2022, 10, 20)) // 0-5 ans
                .telephone("+221777654321")
                .adresse("Medina")
                .region("Dakar")
                .departement("Dakar")
                .commune("Medina")
                .dateEnregistrement(LocalDateTime.now().minusMonths(1))
                .agentId(agent.getId())
                .bureauCsuId(bureauCentral.getId())
                .supprime(false)
                .numeroRegistre("REG-789")
                .matriculeExtraitAccompagnant("EXT-456")
                .datePriseEnCharge(LocalDate.now().minusDays(10))
                .service("Pediatrie")
                .prestationMedicament("Paracetamol syrup, Amoxicillin")
                .diagnosticMotif("Fievre passagere")
                .build();
        patient2 = patientRepository.save(patient2);


        // 5. Seed Enrolements
        Enrolement enr1 = Enrolement.builder()
                .numeroBeneficiaire("BEN-001")
                .patient(patient1)
                .dateEnrolement(LocalDateTime.now().minusMonths(2))
                .statut("VALIDE")
                .agentId(agent.getId())
                .bureauCsuId(bureauCentral.getId())
                .observations("Dossier complet et approuvé")
                .build();
        enrolementRepository.save(enr1);

        Enrolement enr2 = Enrolement.builder()
                .numeroBeneficiaire("BEN-002")
                .patient(patient2)
                .dateEnrolement(LocalDateTime.now().minusMonths(1))
                .statut("EN_COURS")
                .agentId(agent.getId())
                .bureauCsuId(bureauCentral.getId())
                .observations("En attente de validation de l'extrait de naissance")
                .build();
        enrolementRepository.save(enr2);

        // 6. Seed Activites
        Activite act1 = Activite.builder()
                .typeActivite("SENSIBILISATION")
                .description("Sensibilisation au marche central de Grand Yoff")
                .dateActivite(LocalDateTime.now().minusDays(2))
                .agentId(agent.getId())
                .nombreParticipants(45)
                .commentaires("Excellente reception par les commerçants")
                .bureauCsuId(bureauCentral.getId())
                .categorieId(catSensibilisation.getId())
                .build();
        activiteRepository.save(act1);

        Activite act2 = Activite.builder()
                .typeActivite("FORMATION")
                .description("Formation technique sur le nouveau portail d'enrolement")
                .dateActivite(LocalDateTime.now().minusDays(5))
                .agentId(agent.getId())
                .nombreParticipants(12)
                .commentaires("Tous les agents ont ete formes")
                .bureauCsuId(bureauCentral.getId())
                .categorieId(catAtelier.getId())
                .build();
        activiteRepository.save(act2);

        // 7. Seed Constats
        Constat constat1 = Constat.builder()
                .referenceConstat("CST-20260603-001")
                .dateConstat(LocalDateTime.now().minusDays(1))
                .description("Dysfonctionnement de l'imprimante thermique du bureau")
                .priorite("MOYENNE")
                .statut("OUVERT")
                .responsableId(admin.getId())
                .archive(false)
                .bureauCsuId(bureauCentral.getId())
                .categorieId(catMateriel.getId())
                .build();
        constatRepository.save(constat1);

        Constat constat2 = Constat.builder()
                .referenceConstat("CST-20260603-002")
                .dateConstat(LocalDateTime.now())
                .description("Lenteur d'affichage lors de la recherche par N. Dossier")
                .priorite("HAUTE")
                .statut("EN_COURS")
                .responsableId(admin.getId())
                .archive(false)
                .bureauCsuId(bureauCentral.getId())
                .categorieId(catLogiciel.getId())
                .build();
        constatRepository.save(constat2);
    }
}
