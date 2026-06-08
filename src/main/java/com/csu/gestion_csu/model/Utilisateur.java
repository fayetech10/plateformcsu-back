package com.csu.gestion_csu.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "utilisateurs", indexes = {
        @Index(name = "idx_user_bureau", columnList = "bureauId"),
        @Index(name = "idx_user_role", columnList = "role"),
        @Index(name = "idx_user_actif", columnList = "actif")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Utilisateur implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role; // ADMIN, SUPERVISEUR, AGENT

    private String email;
    private String telephone;

    private Long bureauId;
    private Long structureId;

    private java.time.LocalDateTime dateCreation;

    // Champ d'affichage (non persisté) : nom du bureau résolu
    @Transient
    private String bureauCsuNom;

    @Column(columnDefinition = "boolean default true")
    private boolean actif = true;

    // L'utilisateur doit changer son mot de passe par défaut à la première connexion
    @Column(columnDefinition = "boolean default true")
    @Builder.Default
    private boolean doitChangerMotDePasse = true;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return actif;
    }
}
