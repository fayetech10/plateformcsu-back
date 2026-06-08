package com.csu.gestion_csu.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bureaux")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bureau {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    private String code;
    private String region;
    private String departement;
    private String commune;
    private String adresse;
    private String telephone;
    
    @Builder.Default
    private boolean actif = true;

    private String type; // "CSU" ou "Structure de Santé"

    // Géolocalisation pour le contrôle de pointage (géofencing)
    private Double latitude;
    private Double longitude;

    @Column(columnDefinition = "integer default 150")
    @Builder.Default
    private Integer rayonToleranceMetres = 150;
}
