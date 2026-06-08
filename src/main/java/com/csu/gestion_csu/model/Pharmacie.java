package com.csu.gestion_csu.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "pharmacies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pharmacie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    private String responsable;
    private String region;
    private String departement;
    private String commune;
    private String adresse;
    private String telephone;
    private String email;

    // Convention
    private String numeroConvention;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StatutConvention statutConvention = StatutConvention.EN_ATTENTE;

    private LocalDate dateSignature;
    private LocalDate dateExpiration;

    @Column(length = 1000)
    private String notes;

    // Géolocalisation pour la cartographie
    private Double latitude;
    private Double longitude;

    public enum StatutConvention {
        SIGNEE,      // Convention signée
        ARRETEE,     // Convention arrêtée / résiliée
        EN_ATTENTE,  // En cours de négociation / en attente de signature
        EXPIREE,     // Convention expirée
        AUTRE
    }
}
