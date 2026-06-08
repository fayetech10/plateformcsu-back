package com.csu.gestion_csu.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Personne à charge rattachée à un enrôlement (adhésion familiale).
 * Alimente le repeat group "group_pers_charge" (Form 1) et "pers_charge" (Form 2) côté Kobo.
 */
@Entity
@Table(name = "personnes_a_charge")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonneACharge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String prenom;
    private String nom;
    private String sexe;            // M / F
    private LocalDate dateNaissance;
    private String lienParente;     // code Kobo : conjoint_e, fils, fille, père, mere, ...
    private String telephone;
}
