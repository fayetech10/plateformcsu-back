package com.csu.gestion_csu.repository;

import com.csu.gestion_csu.model.Bureau;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BureauRepository extends JpaRepository<Bureau, Long> {
    Page<Bureau> findByNomContainingIgnoreCaseOrCodeContainingIgnoreCase(String nom, String code, Pageable pageable);
    List<Bureau> findByActif(boolean actif);
}
