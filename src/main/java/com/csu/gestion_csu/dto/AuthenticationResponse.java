package com.csu.gestion_csu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationResponse {
    private String token;
    private String username;
    private String role;
    private String nom;
    private String prenom;
    private Long agent_id;
    private Long bureau_id;
    private Long structure_id;
    private String bureauCsuNom;
    private String structureNom;
    private boolean doitChangerMotDePasse;
}
