package com.csu.gestion_csu.controller;

import com.csu.gestion_csu.model.Categorie;
import com.csu.gestion_csu.repository.CategorieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CategorieController {

    private final CategorieRepository categorieRepository;

    // ── ACTIVITES CATEGORIES ──

    @GetMapping("/activites")
    public ResponseEntity<List<Categorie>> getActiviteCategories(@RequestParam(defaultValue = "false") boolean actifOnly) {
        if (actifOnly) {
            return ResponseEntity.ok(categorieRepository.findByTypeAndActif("ACTIVITE", true));
        }
        return ResponseEntity.ok(categorieRepository.findByType("ACTIVITE"));
    }

    @PostMapping("/activites")
    public ResponseEntity<Categorie> createActiviteCategory(@RequestBody Categorie categorie) {
        categorie.setType("ACTIVITE");
        return ResponseEntity.ok(categorieRepository.save(categorie));
    }

    @PutMapping("/activites/{id}")
    public ResponseEntity<Categorie> updateActiviteCategory(@PathVariable Long id, @RequestBody Categorie categorieDetails) {
        return categorieRepository.findById(id)
                .map(existing -> {
                    existing.setNom(categorieDetails.getNom());
                    existing.setDescription(categorieDetails.getDescription());
                    existing.setActif(categorieDetails.isActif());
                    return ResponseEntity.ok(categorieRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── CONSTATS CATEGORIES ──

    @GetMapping("/constats")
    public ResponseEntity<List<Categorie>> getConstatCategories(@RequestParam(defaultValue = "false") boolean actifOnly) {
        if (actifOnly) {
            return ResponseEntity.ok(categorieRepository.findByTypeAndActif("CONSTAT", true));
        }
        return ResponseEntity.ok(categorieRepository.findByType("CONSTAT"));
    }

    @PostMapping("/constats")
    public ResponseEntity<Categorie> createConstatCategory(@RequestBody Categorie categorie) {
        categorie.setType("CONSTAT");
        return ResponseEntity.ok(categorieRepository.save(categorie));
    }

    @PutMapping("/constats/{id}")
    public ResponseEntity<Categorie> updateConstatCategory(@PathVariable Long id, @RequestBody Categorie categorieDetails) {
        return categorieRepository.findById(id)
                .map(existing -> {
                    existing.setNom(categorieDetails.getNom());
                    existing.setDescription(categorieDetails.getDescription());
                    existing.setActif(categorieDetails.isActif());
                    return ResponseEntity.ok(categorieRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
