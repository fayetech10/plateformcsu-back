package com.csu.gestion_csu.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bons_commande", indexes = {
        @Index(name = "idx_bon_patient", columnList = "patientId"),
        @Index(name = "idx_bon_agent", columnList = "agentId"),
        @Index(name = "idx_bon_pharmacie", columnList = "pharmacieId"),
        @Index(name = "idx_bon_statut", columnList = "statut")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BonCommande {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String reference; // Ex: BC-2026-000123

    @Column(nullable = false)
    private LocalDateTime dateCreation;

    // Patient destinataire (avec instantanés pour l'impression)
    private Long patientId;
    private String patientNom;       // "Prénom Nom"
    private String numeroDossier;

    // Agent émetteur
    private Long agentId;
    private String agentNom;

    // Bureau de rattachement
    private Long bureauCsuId;

    // Référence à la lettre de garantie (= dossier patient enregistré)
    private String referenceLettreGarantie;

    // Contexte médical : ordonnance émise par le médecin de l'établissement
    private String medecinPrescripteur;   // Médecin ayant établi l'ordonnance
    private String serviceHopital;        // Établissement / service ne disposant pas des médicaments
    private LocalDate dateOrdonnance;     // Date de l'ordonnance

    @Builder.Default
    private String motif = "Médicaments non disponibles à l'établissement de santé"; // Justification du bon

    // Pharmacie conventionnée suggérée (instantanés)
    private Long pharmacieId;
    private String pharmacieNom;
    private String pharmacieAdresse;
    private String pharmacieTelephone;

    @Column(nullable = false)
    @Builder.Default
    private String statut = "EN_ATTENTE"; // EN_ATTENTE, DELIVRE, ANNULE

    @Column(columnDefinition = "TEXT")
    private String observations;

    private Double montantEstime;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "bon_commande_lignes", joinColumns = @JoinColumn(name = "bon_commande_id"))
    @Builder.Default
    private List<LigneCommande> lignes = new ArrayList<>();
}
