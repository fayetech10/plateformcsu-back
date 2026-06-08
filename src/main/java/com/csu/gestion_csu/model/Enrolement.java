package com.csu.gestion_csu.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

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

    @Column(columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime dateEnrolement;

    @Column(nullable = false)
    private String statut; // EN_COURS, VALIDE, REJETE, SUSPENDU

    private Long agentId;

    @Column(columnDefinition = "TEXT")
    private String observations;

    private Long bureauCsuId;

    // Champs d'affichage (non persistés) : noms résolus de l'agent et du bureau
    @Transient
    private String agentNom;
    @Transient
    private String bureauCsuNom;
}
