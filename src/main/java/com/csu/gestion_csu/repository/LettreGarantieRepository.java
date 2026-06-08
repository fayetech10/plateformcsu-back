package com.csu.gestion_csu.repository;

import com.csu.gestion_csu.model.LettreGarantie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LettreGarantieRepository extends JpaRepository<LettreGarantie, Long> {

    List<LettreGarantie> findByPatientIdOrderByDateEmissionDesc(Long patientId);

    /** Lettre encore valable la plus récente pour un patient (expiration >= date donnée). */
    Optional<LettreGarantie> findFirstByPatientIdAndDateExpirationGreaterThanEqualOrderByDateEmissionDesc(
            Long patientId, LocalDate date);

    /** Lettre encore valable la plus récente pour un CNI (suit la personne même si réenregistrée). */
    Optional<LettreGarantie> findFirstByNumeroCniIgnoreCaseAndDateExpirationGreaterThanEqualOrderByDateEmissionDesc(
            String numeroCni, LocalDate date);

    long countByReferenceStartingWith(String prefix);

    List<LettreGarantie> findByNumeroCniIgnoreCaseOrderByDateEmissionDesc(String numeroCni);

    org.springframework.data.domain.Page<LettreGarantie> findByReferenceContainingIgnoreCaseOrPatientNomContainingIgnoreCase(
            String reference, String patientNom, org.springframework.data.domain.Pageable pageable);

    List<LettreGarantie> findTop40ByOrderByDateEmissionDesc();

    @org.springframework.data.jpa.repository.Query("SELECT l.bureauCsuId, COUNT(l) FROM LettreGarantie l WHERE l.bureauCsuId IS NOT NULL GROUP BY l.bureauCsuId")
    List<Object[]> countByBureauGrouped();

    @org.springframework.data.jpa.repository.Query("SELECT l.agentId, COUNT(l) FROM LettreGarantie l WHERE l.agentId IS NOT NULL GROUP BY l.agentId")
    List<Object[]> countByAgentGrouped();
}
