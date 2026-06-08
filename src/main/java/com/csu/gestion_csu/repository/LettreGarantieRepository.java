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

    List<LettreGarantie> findTop40ByOrderByDateEmissionDesc();
}
