package com.csu.gestion_csu.controller;

import com.csu.gestion_csu.model.Bureau;
import com.csu.gestion_csu.repository.BureauRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bureaux")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BureauController {

    private final BureauRepository bureauRepository;

    @GetMapping
    public ResponseEntity<Page<Bureau>> getBureaux(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        Pageable pageable = PageRequest.of(page, size);
        if (search != null && !search.trim().isEmpty()) {
            return ResponseEntity.ok(bureauRepository.findByNomContainingIgnoreCaseOrCodeContainingIgnoreCase(search, search, pageable));
        }
        return ResponseEntity.ok(bureauRepository.findAll(pageable));
    }

    @GetMapping("/all")
    public ResponseEntity<List<Bureau>> getAllBureaux(@RequestParam(defaultValue = "false") boolean actifOnly) {
        if (actifOnly) {
            return ResponseEntity.ok(bureauRepository.findByActif(true));
        }
        return ResponseEntity.ok(bureauRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Bureau> getBureauById(@PathVariable Long id) {
        return bureauRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Bureau> createBureau(@RequestBody Bureau bureau) {
        return ResponseEntity.ok(bureauRepository.save(bureau));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Bureau> updateBureau(@PathVariable Long id, @RequestBody Bureau bureauDetails) {
        return bureauRepository.findById(id)
                .map(existing -> {
                    existing.setNom(bureauDetails.getNom());
                    existing.setCode(bureauDetails.getCode());
                    existing.setRegion(bureauDetails.getRegion());
                    existing.setDepartement(bureauDetails.getDepartement());
                    existing.setCommune(bureauDetails.getCommune());
                    existing.setAdresse(bureauDetails.getAdresse());
                    existing.setTelephone(bureauDetails.getTelephone());
                    existing.setActif(bureauDetails.isActif());
                    existing.setType(bureauDetails.getType());
                    existing.setLatitude(bureauDetails.getLatitude());
                    existing.setLongitude(bureauDetails.getLongitude());
                    existing.setRayonToleranceMetres(bureauDetails.getRayonToleranceMetres());
                    return ResponseEntity.ok(bureauRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBureau(@PathVariable Long id) {
        if (bureauRepository.existsById(id)) {
            bureauRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
