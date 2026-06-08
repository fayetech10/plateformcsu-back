package com.csu.gestion_csu.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LigneCommande {

    @Column(nullable = false)
    private String designation; // Médicament / prestation

    private Integer quantite;

    private String posologie;   // Instructions / posologie

    private Double prixUnitaire;
}
