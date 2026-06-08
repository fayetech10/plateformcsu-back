package com.csu.gestion_csu.repository;

import com.csu.gestion_csu.model.Activite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActiviteRepository extends JpaRepository<Activite, Long> {
    long countByBureauCsuId(Long bureauCsuId);
    java.util.List<Activite> findByBureauCsuIdOrderByDateActiviteDesc(Long bureauCsuId);

    /** Dernières activités enregistrées — flux d'activité admin. */
    java.util.List<Activite> findTop40ByOrderByDateActiviteDesc();

    /** Count groupés — utilisés par le dashboard admin pour éviter le N+1. */
    @org.springframework.data.jpa.repository.Query(
        "SELECT a.bureauCsuId, COUNT(a) FROM Activite a WHERE a.bureauCsuId IS NOT NULL GROUP BY a.bureauCsuId")
    java.util.List<Object[]> countByBureauGrouped();

    @org.springframework.data.jpa.repository.Query(
        "SELECT a.agentId, COUNT(a) FROM Activite a WHERE a.agentId IS NOT NULL GROUP BY a.agentId")
    java.util.List<Object[]> countByAgentGrouped();
    @org.springframework.data.jpa.repository.Query("SELECT a FROM Activite a WHERE " +
            "(:bureauId IS NULL OR a.bureauCsuId = :bureauId) AND " +
            "(:typeActivite IS NULL OR :typeActivite = '' OR a.typeActivite = :typeActivite) AND " +
            "(:statut IS NULL OR :statut = '' OR a.statut = :statut) AND " +
            "(:search IS NULL OR :search = '' OR " +
            "LOWER(a.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(a.commentaires) LIKE LOWER(CONCAT('%', :search, '%')))")
    org.springframework.data.domain.Page<Activite> searchActivites(
            @org.springframework.data.repository.query.Param("bureauId") Long bureauId,
            @org.springframework.data.repository.query.Param("typeActivite") String typeActivite,
            @org.springframework.data.repository.query.Param("statut") String statut,
            @org.springframework.data.repository.query.Param("search") String search,
            org.springframework.data.domain.Pageable pageable);



    @org.springframework.data.jpa.repository.Query("SELECT a FROM Activite a WHERE " +
            "a.dateActivite BETWEEN :start AND :end AND " +
            "(:bureauId IS NULL OR a.bureauCsuId = :bureauId) AND " +
            "(:agentId IS NULL OR a.agentId = :agentId)")
    java.util.List<Activite> findActivitesForReport(
            @org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
            @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end,
            @org.springframework.data.repository.query.Param("bureauId") Long bureauId,
            @org.springframework.data.repository.query.Param("agentId") Long agentId);
}

