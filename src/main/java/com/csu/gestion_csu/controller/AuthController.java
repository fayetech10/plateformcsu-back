package com.csu.gestion_csu.controller;

import com.csu.gestion_csu.dto.AuthenticationRequest;
import com.csu.gestion_csu.dto.AuthenticationResponse;
import com.csu.gestion_csu.model.Utilisateur;
import com.csu.gestion_csu.repository.UtilisateurRepository;
import com.csu.gestion_csu.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UtilisateurRepository repository;
    private final com.csu.gestion_csu.repository.BureauRepository bureauRepository;
    private final JwtService jwtService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(@RequestBody AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );
        var user = repository.findByUsername(request.getUsername())
                .orElseThrow();
                
        // Résolution des noms du bureau et de la structure de rattachement
        String bureauNom = (user.getBureauId() != null)
                ? bureauRepository.findById(user.getBureauId()).map(com.csu.gestion_csu.model.Bureau::getNom).orElse(null)
                : null;
        String structureNom = (user.getStructureId() != null)
                ? bureauRepository.findById(user.getStructureId()).map(com.csu.gestion_csu.model.Bureau::getNom).orElse(null)
                : null;

        var claims = new java.util.HashMap<String, Object>();
        claims.put("role", user.getRole());
        claims.put("nom", user.getNom());
        claims.put("prenom", user.getPrenom());
        claims.put("agent_id", user.getId());
        claims.put("bureau_id", user.getBureauId());
        claims.put("structure_id", user.getStructureId());
        claims.put("bureauCsuNom", bureauNom);
        claims.put("structureNom", structureNom);
        claims.put("doitChangerMotDePasse", user.isDoitChangerMotDePasse());

        var jwtToken = jwtService.generateToken(claims, user);

        return ResponseEntity.ok(AuthenticationResponse.builder()
                .token(jwtToken)
                .username(user.getUsername())
                .role(user.getRole())
                .nom(user.getNom())
                .prenom(user.getPrenom())
                .agent_id(user.getId())
                .bureau_id(user.getBureauId())
                .structure_id(user.getStructureId())
                .bureauCsuNom(bureauNom)
                .structureNom(structureNom)
                .doitChangerMotDePasse(user.isDoitChangerMotDePasse())
                .build());
    }

    /** Changement de mot de passe par l'utilisateur connecté. */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest req) {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Utilisateur)) {
            return ResponseEntity.status(401).body(java.util.Map.of("message", "Non authentifié."));
        }
        Utilisateur current = (Utilisateur) auth.getPrincipal();
        Utilisateur user = repository.findById(current.getId()).orElseThrow();

        if (req.getNouveauMotDePasse() == null || req.getNouveauMotDePasse().length() < 6) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "Le nouveau mot de passe doit contenir au moins 6 caractères."));
        }
        if (!passwordEncoder.matches(req.getAncienMotDePasse(), user.getPassword())) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "L'ancien mot de passe est incorrect."));
        }
        if (passwordEncoder.matches(req.getNouveauMotDePasse(), user.getPassword())) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "Le nouveau mot de passe doit être différent de l'ancien."));
        }

        user.setPassword(passwordEncoder.encode(req.getNouveauMotDePasse()));
        user.setDoitChangerMotDePasse(false);
        repository.save(user);
        return ResponseEntity.ok(java.util.Map.of("message", "Mot de passe modifié avec succès."));
    }

    @lombok.Data
    static class ChangePasswordRequest {
        private String ancienMotDePasse;
        private String nouveauMotDePasse;
    }
}
