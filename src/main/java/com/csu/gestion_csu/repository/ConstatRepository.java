package com.csu.gestion_csu.repository;

import com.csu.gestion_csu.model.Constat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConstatRepository extends JpaRepository<Constat, Long> {
    long countByBureauCsuId(Long bureauCsuId);
    java.util.List<Constat> findByBureauCsuIdOrderByDateConstatDesc(Long bureauCsuId);

    /** Derniers constats — flux d'activité admin. */
    java.util.List<Constat> findTop40ByOrderByDateConstatDesc();

    /** Count groupés — évitent le N+1 du dashboard admin. */
    @org.springframework.data.jpa.repository.Query(
        "SELECT c.bureauCsuId, COUNT(c) FROM Constat c WHERE c.bureauCsuId IS NOT NULL GROUP BY c.bureauCsuId")
    java.util.List<Object[]> countByBureauGrouped();

    @org.springframework.data.jpa.repository.Query(
        "SELECT c.responsableId, COUNT(c) FROM Constat c WHERE c.responsableId IS NOT NULL GROUP BY c.responsableId")
    java.util.List<Object[]> countByResponsableGrouped();
    @org.springframework.data.jpa.repository.Query("SELECT c FROM Constat c WHERE " +
            "(:bureauId IS NULL OR c.bureauCsuId = :bureauId) AND " +
            "(:statut IS NULL OR :statut = '' OR c.statut = :statut) AND " +
            "(:priorite IS NULL OR :priorite = '' OR c.priorite = :priorite) AND " +
            "(:search IS NULL OR :search = '' OR " +
            "LOWER(c.referenceConstat) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    org.springframework.data.domain.Page<Constat> searchConstats(
            @org.springframework.data.repository.query.Param("bureauId") Long bureauId,
            @org.springframework.data.repository.query.Param("statut") String statut,
            @org.springframework.data.repository.query.Param("priorite") String priorite,
            @org.springframework.data.repository.query.Param("search") String search,
            org.springframework.data.domain.Pageable pageable);



    @org.springframework.data.jpa.repository.Query("SELECT c FROM Constat c WHERE " +
            "c.dateConstat BETWEEN :start AND :end AND " +
            "(:bureauId IS NULL OR c.bureauCsuId = :bureauId) AND " +
            "(:agentId IS NULL OR c.responsableId = :agentId)")
    java.util.List<Constat> findConstatsForReport(
            @org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
            @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end,
            @org.springframework.data.repository.query.Param("bureauId") Long bureauId,
            @org.springframework.data.repository.query.Param("agentId") Long agentId);
}

