package com.csu.gestion_csu.repository;

import com.csu.gestion_csu.model.Categorie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategorieRepository extends JpaRepository<Categorie, Long> {
    List<Categorie> findByType(String type);
    List<Categorie> findByTypeAndActif(String type, boolean actif);
}
