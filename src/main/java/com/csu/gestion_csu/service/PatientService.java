package com.csu.gestion_csu.service;

import com.csu.gestion_csu.model.Patient;
import com.csu.gestion_csu.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;
    private final com.csu.gestion_csu.repository.EnrolementRepository enrolementRepository;

    public Page<Patient> getAllPatients(Pageable pageable, String search, Long bureauId) {
        return patientRepository.searchPatients(bureauId, search, null, null, null, pageable);
    }

    public Page<Patient> searchPatients(Long bureauId, String search, String sexe, String region, String categorie, Pageable pageable) {
        return patientRepository.searchPatients(bureauId, search, sexe, region, categorie, pageable);
    }

    public java.util.List<Patient> searchPatientsList(Long bureauId, String search, String sexe, String region, String categorie) {
        return patientRepository.searchPatientsList(bureauId, search, sexe, region, categorie);
    }



    public Patient getPatientById(Long id) {
        return patientRepository.findById(id).orElseThrow(() -> new RuntimeException("Patient not found"));
    }

    public Patient createPatient(Patient patient, Long agentId, Long bureauId) {
        patient.setDateEnregistrement(LocalDateTime.now());
        if (patient.getAgentId() == null) {
            patient.setAgentId(agentId);
        }
        if (patient.getBureauCsuId() == null) {
            patient.setBureauCsuId(bureauId);
        }
        return patientRepository.save(patient);
    }


    public Patient updatePatient(Long id, Patient updatedPatient) {
        Patient existing = getPatientById(id);
        existing.setPrenom(updatedPatient.getPrenom());
        existing.setNom(updatedPatient.getNom());
        existing.setSexe(updatedPatient.getSexe());
        existing.setCategorie(updatedPatient.getCategorie());

        existing.setDateNaissance(updatedPatient.getDateNaissance());
        existing.setTelephone(updatedPatient.getTelephone());
        existing.setAdresse(updatedPatient.getAdresse());
        existing.setRegion(updatedPatient.getRegion());
        existing.setDepartement(updatedPatient.getDepartement());
        existing.setCommune(updatedPatient.getCommune());
        existing.setNumeroMatricule(updatedPatient.getNumeroMatricule());
        existing.setNumeroCni(updatedPatient.getNumeroCni());
        existing.setNumeroRegistre(updatedPatient.getNumeroRegistre());
        existing.setMatriculeExtraitAccompagnant(updatedPatient.getMatriculeExtraitAccompagnant());
        existing.setDatePriseEnCharge(updatedPatient.getDatePriseEnCharge());
        existing.setService(updatedPatient.getService());
        existing.setIrcIra(updatedPatient.getIrcIra());
        existing.setPrestationMedicament(updatedPatient.getPrestationMedicament());
        existing.setDiagnosticMotif(updatedPatient.getDiagnosticMotif());
        existing.setIndicationMotifCbt(updatedPatient.getIndicationMotifCbt());
        existing.setNumeroRegistreBloc(updatedPatient.getNumeroRegistreBloc());
        existing.setDateHeureIntervention(updatedPatient.getDateHeureIntervention());
        existing.setDureeHospitalisationJours(updatedPatient.getDureeHospitalisationJours());
        existing.setNbrePoches(updatedPatient.getNbrePoches());
        existing.setNbreSeances(updatedPatient.getNbreSeances());
        existing.setQuantite(updatedPatient.getQuantite());
        existing.setForfait(updatedPatient.getForfait());
        existing.setPrixUnitaire(updatedPatient.getPrixUnitaire());
        existing.setMontantTotal(updatedPatient.getMontantTotal());
        // Handle photos if necessary
        if(updatedPatient.getPhotoIdentiteRecto() != null) {
            existing.setPhotoIdentiteRecto(updatedPatient.getPhotoIdentiteRecto());
        }
        if(updatedPatient.getPhotoIdentiteVerso() != null) {
            existing.setPhotoIdentiteVerso(updatedPatient.getPhotoIdentiteVerso());
        }
        
        return patientRepository.save(existing);
    }

    @org.springframework.transaction.annotation.Transactional
    public void deletePatient(Long id) {
        // Supprime d'abord les enrôlements liés (FK patient_id NOT NULL) pour éviter la
        // violation d'intégrité référentielle, puis le patient lui-même.
        enrolementRepository.deleteByPatient_Id(id);
        patientRepository.deleteById(id);
    }
}
