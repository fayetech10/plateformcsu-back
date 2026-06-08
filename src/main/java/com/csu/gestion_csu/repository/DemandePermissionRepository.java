package com.csu.gestion_csu.repository;

import com.csu.gestion_csu.model.DemandePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DemandePermissionRepository extends JpaRepository<DemandePermission, Long> {
    List<DemandePermission> findByAgentIdOrderByDateDemandeDesc(Long agentId);
    List<DemandePermission> findByStatutOrderByDateDemandeDesc(String statut);
    List<DemandePermission> findAllByOrderByDateDemandeDesc();
    long countByStatut(String statut);
}
