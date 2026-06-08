package com.csu.gestion_csu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "patients", indexes = {
        @Index(name = "idx_patient_bureau", columnList = "bureauCsuId"),
        @Index(name = "idx_patient_agent", columnList = "agentId"),
        @Index(name = "idx_patient_categorie", columnList = "categorie"),
        @Index(name = "idx_patient_supprime", columnList = "supprime"),
        @Index(name = "idx_patient_date_enr", columnList = "dateEnregistrement")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String numeroDossier;

    private String categorie; // classique, 0-5ans, cesarienne, dialyse-peritoneale, hemodialyse, bsf, cec, plan-sesame, ndongo-dara


    @Column(nullable = false)
    private String prenom;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false, length = 1)
    private String sexe;

    @Column(nullable = false)
    private LocalDate dateNaissance;

    @Column(nullable = false)
    private String telephone;

    private String adresse;
    private String region;
    private String departement;
    private String commune;

    @Column(columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime dateEnregistrement;

    private Long agentId;
    private Long bureauCsuId;
    
    @Column(columnDefinition = "boolean default false")
    private boolean supprime;

    // Identifiants (selon catégorie)
    private String numeroMatricule;            // N° Matricule / Code Bénéficiaire / CNI selon catégorie
    private String numeroCni;                  // N° CNI (Plan Sésame)
    private String numeroRegistre;             // N° dans le registre (Enfants -5 ans)
    private String matriculeExtraitAccompagnant; // N° Matricule / Extrait / accompagnant (Enfants -5 ans)

    // Prise en charge / médical
    private LocalDate datePriseEnCharge;
    private String service;
    private String ircIra;                     // IRC / IRA (Dialyse, Hémodialyse)

    @Column(columnDefinition = "TEXT")
    private String prestationMedicament;       // Prestation(s) / Prestations et médicaments

    @Column(columnDefinition = "TEXT")
    private String diagnosticMotif;            // Diagnostic / Motif de consultation

    // Spécifiques Césarienne
    @Column(columnDefinition = "TEXT")
    private String indicationMotifCbt;         // Indication / Motif de CBT
    private String numeroRegistreBloc;         // N° Registre Bloc opératoire
    private LocalDateTime dateHeureIntervention;
    private Integer dureeHospitalisationJours;

    // Spécifiques Dialyse / Hémodialyse
    private Integer nbrePoches;                // Dialyse péritonéale
    private Integer nbreSeances;               // Hémodialyse

    // Facturation (une ligne par patient)
    private Double quantite;                   // Quantité
    private Double forfait;                    // Forfait (Enfants -5 ans)
    private Double prixUnitaire;               // P.U / Prix Unitaire
    private Double montantTotal;               // Montant Total / Prix Total / Montant facturé SEN-CSU
    
    // Identity photos base64 (Optional, usually we store URLs but frontend sends Base64 for now)
    @Lob
    @Column(columnDefinition = "TEXT")
    private String photoIdentiteRecto;
    
    @Lob
    @Column(columnDefinition = "TEXT")
    private String photoIdentiteVerso;

    @org.hibernate.annotations.Formula("(SELECT COUNT(*) FROM bons_commande bc WHERE bc.patient_id = id)")
    private Long totalBonsCommande;

    @org.hibernate.annotations.Formula("(SELECT COUNT(*) FROM enrolements e WHERE e.patient_id = id AND e.statut = 'VALIDE')")
    private Long totalLettresGarantie;

    @Transient
    private String agentNom;

    @Transient
    private String bureauCsuNom;
}
