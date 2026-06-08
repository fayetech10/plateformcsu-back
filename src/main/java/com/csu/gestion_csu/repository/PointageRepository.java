package com.csu.gestion_csu.repository;

import com.csu.gestion_csu.model.Pointage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PointageRepository extends JpaRepository<Pointage, Long> {
    Optional<Pointage> findByAgentIdAndDatePointage(Long agentId, LocalDate datePointage);
    List<Pointage> findByDatePointageOrderByHeureArriveeDesc(LocalDate datePointage);
    List<Pointage> findByAgentIdOrderByDatePointageDescHeureArriveeDesc(Long agentId);
    List<Pointage> findByBureauIdAndDatePointageOrderByHeureArriveeDesc(Long bureauId, LocalDate datePointage);
    List<Pointage> findByDatePointageBetweenOrderByDatePointageDescHeureArriveeDesc(LocalDate debut, LocalDate fin);

    /** Derniers pointages — flux d'activité admin. */
    List<Pointage> findTop40ByOrderByHeureArriveeDesc();

    /**
     * Compte par agent et par classe d'arrivée (À l'heure / En retard) selon l'heure limite.
     * Tuple : [agentId, aLHeure, enRetard]
     */
    @org.springframework.data.jpa.repository.Query(
        "SELECT p.agentId, " +
        "  SUM(CASE WHEN p.heureArrivee IS NOT NULL AND FUNCTION('HOUR', p.heureArrivee) * 60 + FUNCTION('MINUTE', p.heureArrivee) <= :limiteMinutes THEN 1 ELSE 0 END), " +
        "  SUM(CASE WHEN p.heureArrivee IS NOT NULL AND FUNCTION('HOUR', p.heureArrivee) * 60 + FUNCTION('MINUTE', p.heureArrivee) >  :limiteMinutes THEN 1 ELSE 0 END) " +
        "FROM Pointage p WHERE p.agentId IS NOT NULL GROUP BY p.agentId")
    List<Object[]> countPonctualiteByAgent(@org.springframework.data.repository.query.Param("limiteMinutes") int limiteMinutes);
}
