package com.csu.gestion_csu.repository;

import com.csu.gestion_csu.model.Pharmacie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PharmacieRepository extends JpaRepository<Pharmacie, Long> {
    Page<Pharmacie> findByNomContainingIgnoreCaseOrCommuneContainingIgnoreCaseOrRegionContainingIgnoreCase(
            String nom, String commune, String region, Pageable pageable);

    List<Pharmacie> findByStatutConvention(Pharmacie.StatutConvention statutConvention);
}
