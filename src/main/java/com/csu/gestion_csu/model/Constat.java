package com.csu.gestion_csu.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "constats", indexes = {
        @Index(name = "idx_constat_bureau", columnList = "bureauCsuId"),
        @Index(name = "idx_constat_responsable", columnList = "responsableId"),
        @Index(name = "idx_constat_statut", columnList = "statut"),
        @Index(name = "idx_constat_date", columnList = "dateConstat")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Constat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String referenceConstat;

    @Column(nullable = false)
    private LocalDateTime dateConstat;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    private Long categorieId;

    @Column(nullable = false)
    private String priorite; // BASSE, MOYENNE, HAUTE, URGENTE

    @Column(nullable = false)
    private String statut; // OUVERT, EN_COURS, RESOLU, ARCHIVE

    private Long responsableId;

    @Column(columnDefinition = "TEXT")
    private String piecesJointes;

    @Column(columnDefinition = "boolean default false")
    private boolean archive;

    private Long bureauCsuId;
}
