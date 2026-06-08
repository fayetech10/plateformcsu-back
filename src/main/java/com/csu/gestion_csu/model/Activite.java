package com.csu.gestion_csu.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "activites", indexes = {
        @Index(name = "idx_activite_bureau", columnList = "bureauCsuId"),
        @Index(name = "idx_activite_agent", columnList = "agentId"),
        @Index(name = "idx_activite_date", columnList = "dateActivite")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Activite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String typeActivite;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(nullable = false)
    private LocalDateTime dateActivite;

    private Long agentId;

    @Column(nullable = false)
    private Integer nombreParticipants;

    @Column(columnDefinition = "TEXT")
    private String commentaires;

    private Long bureauCsuId;
    private Long categorieId;

    // Statut de l'activité : PLANIFIEE, REALISEE, ANNULEE
    private String statut;
}
