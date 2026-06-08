package com.csu.gestion_csu.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lettre de garantie émise pour un patient. Elle est valable une durée fixe
 * (2 semaines). Tant qu'une lettre est valide, on ne doit pas en réémettre une
 * nouvelle : le patient se soigne avec celle déjà émise.
 */
@Entity
@Table(name = "lettres_garantie", indexes = {
        @Index(name = "idx_lg_patient", columnList = "patientId"),
        @Index(name = "idx_lg_expiration", columnList = "dateExpiration")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LettreGarantie {

    /** Durée de validité d'une lettre de garantie, en jours (1 mois). */
    public static final int VALIDITE_JOURS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String reference; // Ex: LG-2026-000123

    private Long patientId;
    private String patientNom;
    private String numeroDossier;
    private String numeroCni;
    private String categorie;

    private Integer ageBeneficiaire;
    private String sexeBeneficiaire;

    /** Nom de la structure (établissement de santé). */
    private String structure;

    /** Type d'assuré (Titulaire, Ayant-droit, etc.). */
    private String typeAssure;

    /** Code assuré / immatriculation. */
    private String codeAssureImmatriculation;

    /** Motif de la prise en charge. */
    @Column(columnDefinition = "TEXT")
    private String motif;

    /** Taux de prise en charge (ex: 80%, 100%). */
    private String tauxPriseEnCharge;

    @Column(nullable = false)
    private LocalDateTime dateEmission;

    /** Date jusqu'à laquelle (incluse) la lettre reste valable. */
    @Column(nullable = false)
    private LocalDate dateExpiration;

    private Long agentId;
    private String agentNom;
    private Long bureauCsuId;

    /** Vrai si la lettre est encore valable à la date du jour. */
    @Transient
    public boolean isActive() {
        return dateExpiration != null && !dateExpiration.isBefore(LocalDate.now());
    }
}
