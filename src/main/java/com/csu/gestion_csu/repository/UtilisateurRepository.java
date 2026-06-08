package com.csu.gestion_csu.repository;

import com.csu.gestion_csu.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {
    Optional<Utilisateur> findByUsername(String username);
    org.springframework.data.domain.Page<Utilisateur> findByNomContainingIgnoreCaseOrPrenomContainingIgnoreCaseOrEmailContainingIgnoreCaseOrUsernameContainingIgnoreCase(
            String nom, String prenom, String email, String username, org.springframework.data.domain.Pageable pageable);
    java.util.List<Utilisateur> findByBureauId(Long bureauId);
}
