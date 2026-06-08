package com.csu.gestion_csu.service;

import com.csu.gestion_csu.model.Enrolement;
import com.csu.gestion_csu.model.PersonneACharge;
import com.csu.gestion_csu.repository.EnrolementRepository;
import com.csu.gestion_csu.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pousse chaque enrôlement créé sur la plateforme vers KoboToolbox via
 * l'API de soumission OpenRosa (multipart "xml_submission_file").
 *
 * Le formulaire cible est identifié par son asset_uid, qui sert à la fois
 * de nom de nœud racine et d'id_string côté KoboCAT (routage de la soumission).
 * Mapping calé sur le formulaire "adhésion familiale" (abxHeF6pvaRNWHxmW5AQZT).
 */
@Service
public class KoboSyncService {

    private static final Logger log = LoggerFactory.getLogger(KoboSyncService.class);

    public static final String STATUT_EN_ATTENTE = "EN_ATTENTE";
    public static final String STATUT_SYNCED = "SYNCED";
    public static final String STATUT_ECHEC = "ECHEC";
    public static final String STATUT_NON_SYNC = "NON_SYNC";

    private final EnrolementRepository enrolementRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final RestTemplate restTemplate;

    @Value("${kobo.enabled:false}")
    private boolean enabled;

    @Value("${kobo.submission-url:https://kc.kobotoolbox.org/submission}")
    private String submissionUrl;

    @Value("${kobo.token:}")
    private String token;

    @Value("${kobo.asset-uid:}")
    private String assetUid;

    // Formulaire "Rajout de personnes à charge"
    @Value("${kobo.asset-uid-rajout:}")
    private String assetUidRajout;

    public KoboSyncService(EnrolementRepository enrolementRepository,
                           UtilisateurRepository utilisateurRepository) {
        this.enrolementRepository = enrolementRepository;
        this.utilisateurRepository = utilisateurRepository;
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(20_000);
        this.restTemplate = new RestTemplate(factory);
    }

    /** Synchronisation non bloquante déclenchée après la création d'un enrôlement. */
    @Async
    public void syncAsync(Long enrolementId) {
        try {
            sync(enrolementId);
        } catch (Exception e) {
            log.warn("Echec synchronisation Kobo asynchrone (enrolement {}): {}", enrolementId, e.getMessage());
        }
    }

    /** Synchronisation (re)jouable à la demande. Ne lève jamais d'exception métier. */
    @Transactional
    public Enrolement sync(Long enrolementId) {
        Enrolement e = enrolementRepository.findById(enrolementId).orElse(null);
        if (e == null) {
            return null;
        }

        if (!enabled || token == null || token.isBlank() || assetUid == null || assetUid.isBlank()) {
            e.setKoboSyncStatus(STATUT_NON_SYNC);
            e.setKoboSyncError("Synchronisation Kobo désactivée ou non configurée.");
            e.setKoboSyncDate(LocalDateTime.now());
            return enrolementRepository.save(e);
        }

        // On réutilise l'instanceID existant pour rester idempotent côté Kobo.
        String instanceId = (e.getKoboUuid() != null && e.getKoboUuid().startsWith("uuid:"))
                ? e.getKoboUuid()
                : "uuid:" + UUID.randomUUID();

        String xml = buildInstanceXml(e, instanceId);
        applyOutcome(e, postSubmission(xml), instanceId);
        return enrolementRepository.save(e);
    }

    /**
     * Soumet les personnes à charge de l'enrôlement au formulaire "Rajout de personnes à charge"
     * (Form 2). Utilisé pour le scénario d'ajout de dépendants à un adhérent déjà membre.
     */
    @Transactional
    public Enrolement syncRajout(Long enrolementId) {
        Enrolement e = enrolementRepository.findById(enrolementId).orElse(null);
        if (e == null) {
            return null;
        }
        if (!enabled || token == null || token.isBlank() || assetUidRajout == null || assetUidRajout.isBlank()) {
            e.setKoboSyncError("Formulaire de rajout Kobo désactivé ou non configuré.");
            e.setKoboSyncDate(LocalDateTime.now());
            return enrolementRepository.save(e);
        }
        if (e.getPersonnesACharge() == null || e.getPersonnesACharge().isEmpty()) {
            e.setKoboSyncError("Aucune personne à charge à envoyer (rajout).");
            e.setKoboSyncDate(LocalDateTime.now());
            return enrolementRepository.save(e);
        }

        String instanceId = "uuid:" + UUID.randomUUID();
        String xml = buildRajoutXml(e, instanceId);
        applyOutcome(e, postSubmission(xml), e.getKoboUuid());
        return enrolementRepository.save(e);
    }

    /** Résultat d'une soumission : statut + message d'erreur éventuel. */
    private record SubmissionResult(boolean ok, String error) {}

    /** Poste une instance XForm vers l'endpoint OpenRosa. Ne lève jamais d'exception. */
    private SubmissionResult postSubmission(String xml) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set(HttpHeaders.AUTHORIZATION, "Token " + token);
            headers.set("X-OpenRosa-Version", "1.0");

            ByteArrayResource part = new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8)) {
                @Override
                public String getFilename() {
                    return "submission.xml";
                }
            };
            HttpHeaders partHeaders = new HttpHeaders();
            partHeaders.setContentType(MediaType.APPLICATION_XML);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("xml_submission_file", new HttpEntity<>(part, partHeaders));

            ResponseEntity<String> resp = restTemplate.postForEntity(
                    submissionUrl, new HttpEntity<>(body, headers), String.class);

            int code = resp.getStatusCode().value();
            if (code == 201 || code == 202) {
                return new SubmissionResult(true, null);
            }
            return new SubmissionResult(false, "HTTP " + code + " : " + truncate(resp.getBody()));
        } catch (HttpStatusCodeException ex) {
            return new SubmissionResult(false, "HTTP " + ex.getStatusCode().value() + " : " + truncate(ex.getResponseBodyAsString()));
        } catch (Exception ex) {
            return new SubmissionResult(false, truncate(ex.getMessage()));
        }
    }

    private void applyOutcome(Enrolement e, SubmissionResult result, String instanceId) {
        if (result.ok()) {
            if (instanceId != null) {
                e.setKoboUuid(instanceId);
            }
            e.setKoboSyncStatus(STATUT_SYNCED);
            e.setKoboSyncError(null);
            log.info("Enrolement {} synchronisé vers Kobo ({})", e.getId(), instanceId);
        } else {
            e.setKoboSyncStatus(STATUT_ECHEC);
            e.setKoboSyncError(result.error());
            log.warn("Echec synchronisation Kobo (enrolement {}): {}", e.getId(), result.error());
        }
        e.setKoboSyncDate(LocalDateTime.now());
    }

    /**
     * Construit l'instance XForm pour le formulaire "adhésion familiale".
     * Le nœud racine porte le nom de l'asset_uid, comme le XForm déployé.
     */
    private String buildInstanceXml(Enrolement e, String instanceId) {
        String agentNom = "";
        if (e.getAgentId() != null) {
            agentNom = utilisateurRepository.findById(e.getAgentId())
                    .map(u -> ((u.getPrenom() == null ? "" : u.getPrenom()) + " "
                            + (u.getNom() == null ? "" : u.getNom())).trim())
                    .orElse("");
        }

        String tel = digits(e.getTelephone());
        String autreTel = digits(e.getAutreTelephone());
        String dn = e.getDateNaissance() == null ? "" : e.getDateNaissance().toString(); // yyyy-MM-dd
        boolean aDependants = e.getPersonnesACharge() != null && !e.getPersonnesACharge().isEmpty();

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append('<').append(assetUid).append(" id=\"").append(assetUid).append("\">");

        sb.append("<debut_formulaire>");
        sb.append(el("prenoms_nom_agent_collect", agentNom));
        sb.append("</debut_formulaire>");

        sb.append("<information_affiliation>");
        sb.append(el("region_affiliation", e.getRegionAffiliation()));
        sb.append(el("organisme_assureur", e.getOrganismeAssureur()));
        sb.append(el("ogd", e.getOgd()));
        sb.append(el("type_regime", e.getTypeRegime()));
        sb.append(el("type_beneficiaire", e.getTypeBeneficiaire()));
        sb.append(el("type_adhesion", e.getTypeAdhesion()));
        sb.append("</information_affiliation>");

        sb.append("<information_identification_adherent>");
        sb.append(el("region_residence", e.getRegionResidence()));
        sb.append(el("departement_residence", e.getDepartementResidence()));
        sb.append(el("commune_residence", e.getCommuneResidence()));
        sb.append(el("prenom_adherent", e.getPrenom()));
        sb.append(el("name_adherent", e.getNom()));
        sb.append(el("sexe_adherent", sexeKobo(e.getSexe())));
        sb.append(el("disponibilite_date_naissance_adherent", dn.isEmpty() ? "non_adherent" : "oui_adherent"));
        sb.append(el("date_naissance_adherent", dn));
        sb.append(el("lieu_naissance", e.getLieuNaissance()));
        sb.append(el("adresse_adherent", e.getAdresse()));
        sb.append(el("telephone_whatsapp_adherent", tel));
        sb.append(el("autre_telephone_adherent", autreTel));
        sb.append(el("situation_matrimoniale_adherent", e.getSituationMatrimoniale()));
        sb.append(el("secteur_activite", e.getSecteurActivite()));
        sb.append(el("type_piece_identification_adherent_avec", e.getTypePieceIdentite()));
        sb.append(el("numero_piece_1", e.getNumeroPiece1()));
        sb.append(el("numero_piece_2", e.getNumeroPiece2()));
        sb.append(el("numero_piece_3", e.getNumeroPiece3()));
        sb.append(el("existence_pers_charge", aDependants ? "oui_adherent" : "non_adherent"));
        if (aDependants) {
            for (PersonneACharge p : e.getPersonnesACharge()) {
                sb.append("<group_pers_charge>");
                appendDependantFields(sb, p);
                sb.append("</group_pers_charge>");
            }
        }
        sb.append("</information_identification_adherent>");

        sb.append("<paiement_frais_adhesion_cotisations>");
        sb.append(el("montant_frais_adhesion_adherent", num(e.getMontantFraisAdhesion())));
        sb.append(el("montant_cotisation_adherent", num(e.getMontantCotisation())));
        sb.append(el("moyen_paiement", e.getMoyenPaiement()));
        sb.append(el("montant_versement_integral", num(e.getMontantVersement())));
        sb.append(el("statut_paiement_final_adherent", e.getStatutPaiement()));
        sb.append("</paiement_frais_adhesion_cotisations>");

        sb.append("<meta><instanceID>").append(instanceId).append("</instanceID></meta>");
        sb.append("</").append(assetUid).append('>');
        return sb.toString();
    }

    private String digits(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }

    private String num(Integer n) {
        return n == null ? "" : n.toString();
    }

    /** Instance XForm pour le formulaire "Rajout de personnes à charge" (Form 2, repeat "pers_charge"). */
    private String buildRajoutXml(Enrolement e, String instanceId) {
        String tel = e.getTelephone() == null ? "" : e.getTelephone().replaceAll("[^0-9]", "");
        String dn = e.getDateNaissance() == null ? "" : e.getDateNaissance().toString();

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append('<').append(assetUidRajout).append(" id=\"").append(assetUidRajout).append("\">");

        sb.append("<information_identification_adherent>");
        sb.append(el("prenom_adherent", e.getPrenom()));
        sb.append(el("name_adherent", e.getNom()));
        sb.append(el("sexe_adherent", sexeKobo(e.getSexe())));
        sb.append(el("date_naissance_adherent", dn));
        sb.append(el("telephone_whatsapp_adherent", tel));
        for (PersonneACharge p : e.getPersonnesACharge()) {
            sb.append("<pers_charge>");
            appendDependantFields(sb, p);
            sb.append("</pers_charge>");
        }
        sb.append("</information_identification_adherent>");

        sb.append("<meta><instanceID>").append(instanceId).append("</instanceID></meta>");
        sb.append("</").append(assetUidRajout).append('>');
        return sb.toString();
    }

    /** Champs communs d'une personne à charge (mêmes noms dans les deux formulaires). */
    private void appendDependantFields(StringBuilder sb, PersonneACharge p) {
        sb.append(el("prenom_pers_charge", p.getPrenom()));
        sb.append(el("nom_pers_charge", p.getNom()));
        sb.append(el("sexe_pers_charge", sexeKobo(p.getSexe())));
        sb.append(el("date_naissance_pers_charge",
                p.getDateNaissance() == null ? "" : p.getDateNaissance().toString()));
        sb.append(el("telephone_whatsapp_pers_charge",
                p.getTelephone() == null ? "" : p.getTelephone().replaceAll("[^0-9]", "")));
        sb.append(el("lien_parente_avec_adherent", p.getLienParente()));
    }

    private String sexeKobo(String sexe) {
        if (sexe == null) return "";
        if ("M".equalsIgnoreCase(sexe)) return "masculin";
        if ("F".equalsIgnoreCase(sexe)) return "feminin";
        return "";
    }

    private String el(String tag, String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return "<" + tag + ">" + escapeXml(value) + "</" + tag + ">";
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String truncate(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
