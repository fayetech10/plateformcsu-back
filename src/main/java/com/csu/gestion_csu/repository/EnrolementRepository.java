package com.csu.gestion_csu.repository;

import com.csu.gestion_csu.model.Enrolement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EnrolementRepository extends JpaRepository<Enrolement, Long> {
    long countByBureauCsuId(Long bureauCsuId);
    java.util.List<Enrolement> findByBureauCsuIdOrderByDateEnrolementDesc(Long bureauCsuId);

    /** Derniers enrôlements — flux d'activité admin. */
    java.util.List<Enrolement> findTop40ByOrderByDateEnrolementDesc();

    java.util.List<Enrolement> findByPatient_Id(Long patientId);
    void deleteByPatient_Id(Long patientId);

    /** Count groupés — évitent les N+1 du dashboard admin. */
    @org.springframework.data.jpa.repository.Query(
        "SELECT e.bureauCsuId, COUNT(e) FROM Enrolement e WHERE e.bureauCsuId IS NOT NULL GROUP BY e.bureauCsuId")
    java.util.List<Object[]> countByBureauGrouped();

    @org.springframework.data.jpa.repository.Query(
        "SELECT e.agentId, COUNT(e) FROM Enrolement e WHERE e.agentId IS NOT NULL GROUP BY e.agentId")
    java.util.List<Object[]> countByAgentGrouped();

    @org.springframework.data.jpa.repository.Query("SELECT e FROM Enrolement e WHERE " +
            "(:bureauId IS NULL OR e.bureauCsuId = :bureauId) AND " +
            "(:statut IS NULL OR :statut = '' OR e.statut = :statut) AND " +
            "(:search IS NULL OR :search = '' OR " +
            "LOWER(e.numeroBeneficiaire) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(e.nom) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(e.prenom) LIKE LOWER(CONCAT('%', :search, '%')))")
    org.springframework.data.domain.Page<Enrolement> searchEnrolements(
            @org.springframework.data.repository.query.Param("bureauId") Long bureauId,
            @org.springframework.data.repository.query.Param("statut") String statut,
            @org.springframework.data.repository.query.Param("search") String search,
            org.springframework.data.domain.Pageable pageable);



    @org.springframework.data.jpa.repository.Query("SELECT e FROM Enrolement e WHERE " +
            "e.dateEnrolement BETWEEN :start AND :end AND " +
            "(:bureauId IS NULL OR e.bureauCsuId = :bureauId) AND " +
            "(:agentId IS NULL OR e.agentId = :agentId)")
    java.util.List<Enrolement> findEnrolementsForReport(
            @org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
            @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end,
            @org.springframework.data.repository.query.Param("bureauId") Long bureauId,
            @org.springframework.data.repository.query.Param("agentId") Long agentId);
}

