package com.csu.gestion_csu.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "enrolements", indexes = {
        @Index(name = "idx_enrol_bureau", columnList = "bureauCsuId"),
        @Index(name = "idx_enrol_agent", columnList = "agentId"),
        @Index(name = "idx_enrol_statut", columnList = "statut"),
        @Index(name = "idx_enrol_patient", columnList = "patient_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Enrolement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String numeroBeneficiaire;

    // Lien patient optionnel (un enrôlement n'est plus rattaché à un patient)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    // Identité du bénéficiaire saisie directement sur l'enrôlement (autonome)
    private String prenom;
    private String nom;
    private String telephone;
    private String sexe;
    private java.time.LocalDate dateNaissance;
    private String adresse;

    // === Champs conformes au formulaire Kobo (codes Kobo stockés tels quels) ===
    // Affiliation
    private String regionAffiliation;
    private String organismeAssureur;
    private String ogd;
    private String typeRegime;
    private String typeBeneficiaire;
    private String typeAdhesion;
    // Résidence (cascade)
    private String regionResidence;
    private String departementResidence;
    private String communeResidence;
    // Identité étendue
    private String lieuNaissance;
    private String situationMatrimoniale;
    private String secteurActivite;
    private String autreTelephone;
    // Pièce d'identité
    private String typePieceIdentite;
    private String numeroPiece1;
    private String numeroPiece2;
    private String numeroPiece3;
    // Paiement
    private Integer montantFraisAdhesion;
    private Integer montantCotisation;
    private String moyenPaiement;
    private Integer montantVersement;
    private String statutPaiement;

    @Column(columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime dateEnrolement;

    @Column(nullable = false)
    private String statut; // EN_COURS, VALIDE, REJETE, SUSPENDU

    private Long agentId;

    @Column(columnDefinition = "TEXT")
    private String observations;

    private Long bureauCsuId;

    // Personnes à charge (adhésion familiale)
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "enrolement_id")
    @Builder.Default
    private List<PersonneACharge> personnesACharge = new ArrayList<>();

    // === Synchronisation KoboToolbox ===
    // Identifiant unique de la soumission Kobo (uuid:...) envoyé via meta/instanceID.
    private String koboUuid;
    // EN_ATTENTE, SYNCED, ECHEC, NON_SYNC (désactivé/non configuré)
    private String koboSyncStatus;
    @Column(columnDefinition = "TEXT")
    private String koboSyncError;
    private LocalDateTime koboSyncDate;

    // Champs d'affichage (non persistés) : noms résolus de l'agent et du bureau
    @Transient
    private String agentNom;
    @Transient
    private String bureauCsuNom;
}
