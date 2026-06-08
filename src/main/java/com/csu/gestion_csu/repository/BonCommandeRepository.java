package com.csu.gestion_csu.repository;

import com.csu.gestion_csu.model.BonCommande;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BonCommandeRepository extends JpaRepository<BonCommande, Long> {
    Page<BonCommande> findByReferenceContainingIgnoreCaseOrPatientNomContainingIgnoreCaseOrNumeroDossierContainingIgnoreCase(
            String reference, String patientNom, String numeroDossier, Pageable pageable);

    List<BonCommande> findByPatientIdOrderByDateCreationDesc(Long patientId);

    long countByReferenceStartingWith(String prefix);

    /** Derniers bons de commande — flux d'activité admin. */
    List<BonCommande> findTop40ByOrderByDateCreationDesc();
}
