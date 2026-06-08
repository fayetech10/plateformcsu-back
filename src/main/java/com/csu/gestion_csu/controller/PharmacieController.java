package com.csu.gestion_csu.controller;

import com.csu.gestion_csu.model.Pharmacie;
import com.csu.gestion_csu.repository.PharmacieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pharmacies")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PharmacieController {

    private final PharmacieRepository pharmacieRepository;

    @GetMapping
    public ResponseEntity<Page<Pharmacie>> getPharmacies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        Pageable pageable = PageRequest.of(page, size);
        if (search != null && !search.trim().isEmpty()) {
            return ResponseEntity.ok(
                    pharmacieRepository.findByNomContainingIgnoreCaseOrCommuneContainingIgnoreCaseOrRegionContainingIgnoreCase(
                            search, search, search, pageable));
        }
        return ResponseEntity.ok(pharmacieRepository.findAll(pageable));
    }

    /** Liste complète (non paginée) — utilisée pour la cartographie. */
    @GetMapping("/all")
    public ResponseEntity<List<Pharmacie>> getAllPharmacies() {
        return ResponseEntity.ok(pharmacieRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pharmacie> getPharmacieById(@PathVariable Long id) {
        return pharmacieRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Pharmacie> createPharmacie(@RequestBody Pharmacie pharmacie) {
        return ResponseEntity.ok(pharmacieRepository.save(pharmacie));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Pharmacie> updatePharmacie(@PathVariable Long id, @RequestBody Pharmacie details) {
        return pharmacieRepository.findById(id)
                .map(existing -> {
                    existing.setNom(details.getNom());
                    existing.setResponsable(details.getResponsable());
                    existing.setRegion(details.getRegion());
                    existing.setDepartement(details.getDepartement());
                    existing.setCommune(details.getCommune());
                    existing.setAdresse(details.getAdresse());
                    existing.setTelephone(details.getTelephone());
                    existing.setEmail(details.getEmail());
                    existing.setNumeroConvention(details.getNumeroConvention());
                    existing.setStatutConvention(details.getStatutConvention());
                    existing.setDateSignature(details.getDateSignature());
                    existing.setDateExpiration(details.getDateExpiration());
                    existing.setNotes(details.getNotes());
                    existing.setLatitude(details.getLatitude());
                    existing.setLongitude(details.getLongitude());
                    return ResponseEntity.ok(pharmacieRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePharmacie(@PathVariable Long id) {
        if (pharmacieRepository.existsById(id)) {
            pharmacieRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
