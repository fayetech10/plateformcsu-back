package com.csu.gestion_csu.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Demande de permission/absence soumise par un agent et traitée par un admin.
 * statut : EN_ATTENTE → APPROUVEE / REFUSEE
 */
@Entity
@Table(name = "demandes_permission")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemandePermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long agentId;

    private Long bureauId;

    @Column(nullable = false)
    private String type; // CONGE, ABSENCE, RETARD, SORTIE, AUTRE

    @Column(nullable = false)
    private LocalDate dateDebut;

    @Column(nullable = false)
    private LocalDate dateFin;

    @Column(columnDefinition = "TEXT")
    private String motif;

    @Column(nullable = false)
    @Builder.Default
    private String statut = "EN_ATTENTE";

    @Column(nullable = false)
    private LocalDateTime dateDemande;

    private Long traiteePar;
    private LocalDateTime dateTraitement;

    @Column(columnDefinition = "TEXT")
    private String commentaireAdmin;
}
