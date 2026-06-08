package com.csu.gestion_csu.repository;

import com.csu.gestion_csu.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
    long countByBureauCsuId(Long bureauCsuId);
    java.util.List<Patient> findByBureauCsuId(Long bureauCsuId);

    /** Derniers patients enregistrés — flux d'activité admin. */
    java.util.List<Patient> findTop40ByOrderByDateEnregistrementDesc();

    /** Recherche d'un patient existant par identifiant (déduplication). */
    java.util.Optional<Patient> findFirstByNumeroCniIgnoreCaseAndSupprimeFalse(String numeroCni);
    java.util.Optional<Patient> findFirstByNumeroMatriculeIgnoreCaseAndSupprimeFalse(String numeroMatricule);
    java.util.List<Patient> findByNumeroMatriculeIgnoreCaseAndSupprimeFalse(String numeroMatricule);

    /** Count groupé par bureau — évite le N+1 dans le dashboard admin. */
    @org.springframework.data.jpa.repository.Query(
        "SELECT p.bureauCsuId, COUNT(p) FROM Patient p WHERE p.bureauCsuId IS NOT NULL GROUP BY p.bureauCsuId")
    java.util.List<Object[]> countByBureauGrouped();

    /** Count groupé par agent. */
    @org.springframework.data.jpa.repository.Query(
        "SELECT p.agentId, COUNT(p) FROM Patient p WHERE p.agentId IS NOT NULL GROUP BY p.agentId")
    java.util.List<Object[]> countByAgentGrouped();

    /** Count groupé par région / département / commune / année. */
    @org.springframework.data.jpa.repository.Query(
        "SELECT p.region, COUNT(p) FROM Patient p GROUP BY p.region")
    java.util.List<Object[]> countByRegionGrouped();

    @org.springframework.data.jpa.repository.Query(
        "SELECT p.departement, COUNT(p) FROM Patient p GROUP BY p.departement")
    java.util.List<Object[]> countByDepartementGrouped();

    @org.springframework.data.jpa.repository.Query(
        "SELECT p.commune, COUNT(p) FROM Patient p GROUP BY p.commune")
    java.util.List<Object[]> countByCommuneGrouped();

    @org.springframework.data.jpa.repository.Query(
        "SELECT YEAR(p.dateEnregistrement), COUNT(p) FROM Patient p GROUP BY YEAR(p.dateEnregistrement)")
    java.util.List<Object[]> countByAnneeGrouped();
    @org.springframework.data.jpa.repository.Query("SELECT p FROM Patient p WHERE " +
            "(:bureauId IS NULL OR p.bureauCsuId = :bureauId) AND " +
            "(:sexe IS NULL OR :sexe = '' OR p.sexe = :sexe) AND " +
            "(:region IS NULL OR :region = '' OR p.region = :region) AND " +
            "(:categorie IS NULL OR :categorie = '' OR p.categorie = :categorie) AND " +
            "(:search IS NULL OR :search = '' OR " +
            "LOWER(p.nom) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(p.prenom) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(p.numeroDossier) LIKE LOWER(CONCAT('%', :search, '%')))")
    org.springframework.data.domain.Page<Patient> searchPatients(
            @org.springframework.data.repository.query.Param("bureauId") Long bureauId,
            @org.springframework.data.repository.query.Param("search") String search,
            @org.springframework.data.repository.query.Param("sexe") String sexe,
            @org.springframework.data.repository.query.Param("region") String region,
            @org.springframework.data.repository.query.Param("categorie") String categorie,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM Patient p WHERE " +
            "(:bureauId IS NULL OR p.bureauCsuId = :bureauId) AND " +
            "(:sexe IS NULL OR :sexe = '' OR p.sexe = :sexe) AND " +
            "(:region IS NULL OR :region = '' OR p.region = :region) AND " +
            "(:categorie IS NULL OR :categorie = '' OR p.categorie = :categorie) AND " +
            "(:search IS NULL OR :search = '' OR " +
            "LOWER(p.nom) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(p.prenom) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(p.numeroDossier) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "ORDER BY p.dateEnregistrement DESC")
    java.util.List<Patient> searchPatientsList(
            @org.springframework.data.repository.query.Param("bureauId") Long bureauId,
            @org.springframework.data.repository.query.Param("search") String search,
            @org.springframework.data.repository.query.Param("sexe") String sexe,
            @org.springframework.data.repository.query.Param("region") String region,
            @org.springframework.data.repository.query.Param("categorie") String categorie);



    @org.springframework.data.jpa.repository.Query("SELECT p FROM Patient p WHERE " +
            "p.dateEnregistrement BETWEEN :start AND :end AND " +
            "(:bureauId IS NULL OR p.bureauCsuId = :bureauId) AND " +
            "(:agentId IS NULL OR p.agentId = :agentId)")
    java.util.List<Patient> findPatientsForReport(
            @org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
            @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end,
            @org.springframework.data.repository.query.Param("bureauId") Long bureauId,
            @org.springframework.data.repository.query.Param("agentId") Long agentId);
}

