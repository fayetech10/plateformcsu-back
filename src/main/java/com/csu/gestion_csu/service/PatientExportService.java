package com.csu.gestion_csu.service;

import com.csu.gestion_csu.model.Patient;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Génération des exports (PDF / Excel) de la liste des patients.
 *
 * <p>L'export Excel reproduit fidèlement la feuille correspondant à la catégorie
 * du classeur officiel « GESTION DES FACTURES CS_EPS » : entête de structure,
 * titre rouge, ligne Mois/Année, en-têtes gris bordés, volets figés, largeurs
 * de colonnes et libellés exacts.</p>
 */
@Service
public class PatientExportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final String[] MOIS_FR = {
            "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
            "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
    };

    // ──────────────────────────────────────────────────────────────────────
    //  Libellés de catégorie
    // ──────────────────────────────────────────────────────────────────────
    public static String categorieLabel(String code) {
        if (code == null) return "Autre";
        switch (code) {
            case "classique": return "Classique";
            case "0-5ans": return "Enfants de moins de 5 ans";
            case "cesarienne": return "Césarienne";
            case "dialyse-peritoneale": return "Dialyse péritonéale";
            case "hemodialyse": return "Hémodialyse";
            case "bsf": return "Bourse de Sécurité Familiale";
            case "cec": return "Carte Égalité des Chances";
            case "plan-sesame": return "Plan Sésame";
            case "ndongo-dara": return "Plan Ndongo Dara / Élève";
            default: return code;
        }
    }

    private String s(String v) { return v == null ? "" : v; }

    // ──────────────────────────────────────────────────────────────────────
    //  Définition d'une colonne d'une feuille
    // ──────────────────────────────────────────────────────────────────────
    private static class Col {
        final String label;
        final double width;            // largeur en « caractères » Excel
        final Function<Patient, Object> value;
        final boolean numeric;

        Col(String label, double width, Function<Patient, Object> value, boolean numeric) {
            this.label = label;
            this.width = width;
            this.value = value;
            this.numeric = numeric;
        }
    }

    private static class Modele {
        final String titre;
        final List<Col> colonnes;
        Modele(String titre, List<Col> colonnes) {
            this.titre = titre;
            this.colonnes = colonnes;
        }
    }

    // Helpers de valeur
    private static Object date(LocalDate d) { return d == null ? "" : d.format(DATE); }
    private static Object dateH(LocalDateTime d) { return d == null ? "" : d.format(DATE_HEURE); }
    private static String sexeHF(Patient p) {
        if (p.getSexe() == null) return "";
        return p.getSexe().equalsIgnoreCase("M") ? "H" : p.getSexe().toUpperCase();
    }
    private static String sexeMF(Patient p) {
        return p.getSexe() == null ? "" : p.getSexe().toUpperCase();
    }

    // Colonnes communes réutilisables
    private static Col cPrenom() { return new Col("Prénom(s) *", 15.38, p -> nz(p.getPrenom()), false); }
    private static Col cNom() { return new Col("Nom *", 6.63, p -> nz(p.getNom()), false); }
    private static Col cDateNaiss() { return new Col("Date de naissance *", 15.38, p -> date(p.getDateNaissance()), false); }
    private static Col cAdresse() { return new Col("Adresse (optionnel)", 15.38, p -> nz(p.getAdresse()), false); }

    private static String nz(String v) { return v == null ? "" : v; }

    /**
     * Construit le modèle de feuille pour une catégorie donnée.
     * L'ordre, les libellés exacts et les largeurs reproduisent le classeur officiel.
     */
    private Modele modele(String categorie) {
        String cat = (categorie == null) ? "" : categorie;
        List<Col> c = new ArrayList<>();
        switch (cat) {
            case "0-5ans": {
                c.add(new Col("N° dans le registre *", 57.63, p -> nz(p.getNumeroRegistre()), false));
                c.add(new Col("Prénom(s) *", 9.75, p -> nz(p.getPrenom()), false));
                c.add(new Col("Nom *", 5.5, p -> nz(p.getNom()), false));
                c.add(new Col("Date de naissance *", 15.38, p -> date(p.getDateNaissance()), false));
                c.add(new Col("Sexe (M/F) *", 10.13, PatientExportService::sexeMF, false));
                c.add(new Col("Adresse (optionnel)", 15.38, p -> nz(p.getAdresse()), false));
                c.add(new Col("N° Matricule / N° Extrait de naissance / N° accompagnant *", 44.0, p -> nz(p.getMatriculeExtraitAccompagnant()), false));
                c.add(new Col("N°Téléphone (optionnel)", 18.75, p -> nz(p.getTelephone()), false));
                c.add(new Col("Date de prise en Charge *", 19.5, p -> date(p.getDatePriseEnCharge()), false));
                c.add(new Col("Service *", 7.5, p -> nz(p.getService()), false));
                c.add(new Col("Prestations et médicaments", 21.5, p -> nz(p.getPrestationMedicament()), false));
                c.add(new Col("Diagnostic/ Motif de consultation", 25.38, p -> nz(p.getDiagnosticMotif()), false));
                c.add(new Col("Forfait", 5.75, Patient::getForfait, true));
                c.add(new Col("Montant Total", 11.0, Patient::getMontantTotal, true));
                return new Modele("LISTE NOMINATIVE DES ENFANTS DE MOINS DE 5 ANS", c);
            }
            case "cesarienne": {
                c.add(new Col("Prénom (s) *", 42.0, p -> nz(p.getPrenom()), false));
                c.add(new Col("Nom *", 6.63, p -> nz(p.getNom()), false));
                c.add(new Col("Date de naissance *", 15.38, p -> date(p.getDateNaissance()), false));
                c.add(new Col("Sexe (H/F) *", 9.88, PatientExportService::sexeHF, false));
                c.add(new Col("Adresse (optionnel)", 15.38, p -> nz(p.getAdresse()), false));
                c.add(new Col("N° Téléphone (optionnel)", 19.13, p -> nz(p.getTelephone()), false));
                c.add(new Col("N° Matricule / N° CNI Patient / N° accompagnant *", 37.5, p -> nz(p.getNumeroMatricule()), false));
                c.add(new Col("Indication /Motif de CBT", 18.75, p -> nz(p.getIndicationMotifCbt()), false));
                c.add(new Col("N° Registre Bloc opératoire", 20.75, p -> nz(p.getNumeroRegistreBloc()), false));
                c.add(new Col("Date et Heure Intervention", 20.13, p -> dateH(p.getDateHeureIntervention()), false));
                c.add(new Col("Durée Hospitalisation (jours)", 21.88, Patient::getDureeHospitalisationJours, true));
                return new Modele("LISTE NOMINATIVE DE LA CESARIENNE", c);
            }
            case "dialyse-peritoneale": {
                c.add(new Col("Prénom (s) *", 67.38, p -> nz(p.getPrenom()), false));
                c.add(new Col("Nom *", 6.63, p -> nz(p.getNom()), false));
                c.add(new Col("Date de naissance *", 15.38, p -> date(p.getDateNaissance()), false));
                c.add(new Col("Sexe (H/F) *", 9.88, PatientExportService::sexeHF, false));
                c.add(new Col("Adresse (optionnel)", 15.38, p -> nz(p.getAdresse()), false));
                c.add(new Col("N°Téléphone (optionnel)", 18.75, p -> nz(p.getTelephone()), false));
                c.add(new Col("N° Matricule / N° CNI Patient / N° accompagnant *", 37.5, p -> nz(p.getNumeroMatricule()), false));
                c.add(new Col("IRC/IRA *", 7.88, p -> nz(p.getIrcIra()), false));
                c.add(new Col("Date de prise en charge", 18.13, p -> date(p.getDatePriseEnCharge()), false));
                c.add(new Col("Nbre de Poches", 12.5, Patient::getNbrePoches, true));
                c.add(new Col("Prix Unitaire", 10.0, Patient::getPrixUnitaire, true));
                c.add(new Col("Prix Total", 7.75, Patient::getMontantTotal, true));
                return new Modele("ETAT RECAPITULATIF NOMINATIF DE LA DIALYSE PERITONEALE", c);
            }
            case "hemodialyse": {
                c.add(new Col("Prénom (s) *", 56.5, p -> nz(p.getPrenom()), false));
                c.add(new Col("Nom *", 6.63, p -> nz(p.getNom()), false));
                c.add(new Col("Date de naissance *", 15.38, p -> date(p.getDateNaissance()), false));
                c.add(new Col("Sexe (H/F) *", 9.88, PatientExportService::sexeHF, false));
                c.add(new Col("Adresse (optionnel)", 15.38, p -> nz(p.getAdresse()), false));
                c.add(new Col("N°Téléphone (optionnel)", 18.75, p -> nz(p.getTelephone()), false));
                c.add(new Col("N° Matricule / N° CNI Patient / N° accompagnant *", 37.5, p -> nz(p.getNumeroMatricule()), false));
                c.add(new Col("IRC/IRA", 6.75, p -> nz(p.getIrcIra()), false));
                c.add(new Col("Nbre de Séances", 13.25, Patient::getNbreSeances, true));
                c.add(new Col("Prix Unitaire", 10.0, Patient::getPrixUnitaire, true));
                c.add(new Col("Prix Total", 7.75, Patient::getMontantTotal, true));
                return new Modele("ETAT RECAPITULATIF NOMINATIF DE L'HEMODIALYSE", c);
            }
            case "bsf": {
                ajouterColonnesService(c, 78.13, 14.88, "N° Matricule/ CNI *");
                return new Modele("ETAT RECAPITULATIF NOMINATIF DE LA BOURSE DE SECURITE FAMILIALE", c);
            }
            case "cec": {
                ajouterColonnesService(c, 75.75, 14.88, "N° Matricule/ CNI *");
                return new Modele("ETAT RECAPITULATIF NOMINATIF DE LA CARTE EGALITE DES CHANCES", c);
            }
            case "plan-sesame": {
                c.add(new Col("Prénom(s) *", 55.63, p -> nz(p.getPrenom()), false));
                c.add(new Col("Nom *", 6.63, p -> nz(p.getNom()), false));
                c.add(new Col("Date de naissance *", 15.38, p -> date(p.getDateNaissance()), false));
                c.add(new Col("Sexe (H/F) *", 9.88, PatientExportService::sexeHF, false));
                c.add(new Col("Adresse (optionnel)", 15.38, p -> nz(p.getAdresse()), false));
                c.add(new Col("N° Tél (optionnel)", 13.75, p -> nz(p.getTelephone()), false));
                c.add(new Col("N° Matricule *", 11.25, p -> nz(p.getNumeroMatricule()), false));
                c.add(new Col("N° CNI *", 7.0, p -> nz(p.getNumeroCni()), false));
                c.add(new Col("Date de prise en charge *", 19.25, p -> date(p.getDatePriseEnCharge()), false));
                c.add(new Col("Service *", 7.5, p -> nz(p.getService()), false));
                c.add(new Col("Prestation(s)", 10.5, p -> nz(p.getPrestationMedicament()), false));
                c.add(new Col("Quantité", 7.13, Patient::getQuantite, true));
                c.add(new Col("P.U", 3.25, Patient::getPrixUnitaire, true));
                c.add(new Col("Montant facturé à la SEN-CSU", 22.88, Patient::getMontantTotal, true));
                return new Modele("ETAT RECAPITULATIF NOMINATIF DU PLAN SESAME", c);
            }
            case "ndongo-dara": {
                ajouterColonnesService(c, 70.13, 25.5, "N° Matricule / Code Bénéficiaire *");
                // Dernière colonne « Montant Total » (largeur 11) au lieu de « Montant facturé »
                c.set(c.size() - 1, new Col("Montant Total", 11.0, Patient::getMontantTotal, true));
                return new Modele("ETAT RECAPITULATIF NOMINATIF DU PLAN NDONGO DARA/ELEVE", c);
            }
            case "classique":
            default: {
                c.add(new Col("Prénom(s) *", 61.25, p -> nz(p.getPrenom()), false));
                c.add(new Col("Nom *", 6.63, p -> nz(p.getNom()), false));
                c.add(new Col("Date de naissance *", 15.38, p -> date(p.getDateNaissance()), false));
                c.add(new Col("Sexe (H/F) *", 9.88, PatientExportService::sexeHF, false));
                c.add(new Col("Adresse (optionnel)", 15.38, p -> nz(p.getAdresse()), false));
                c.add(new Col("N° Tél (optionnel)", 13.75, p -> nz(p.getTelephone()), false));
                c.add(new Col("N° Matricule / Code Bénéficiaire *", 25.5, p -> nz(p.getNumeroMatricule()), false));
                c.add(new Col("Date de prise en charge *", 19.25, p -> date(p.getDatePriseEnCharge()), false));
                c.add(new Col("Service *", 7.5, p -> nz(p.getService()), false));
                c.add(new Col("Prestation(s)", 10.5, p -> nz(p.getPrestationMedicament()), false));
                c.add(new Col("Quantité", 7.13, Patient::getQuantite, true));
                c.add(new Col("P.U", 3.25, Patient::getPrixUnitaire, true));
                c.add(new Col("Montant Total", 11.0, Patient::getMontantTotal, true));
                return new Modele("ETAT RECAPITULATIF NOMINATIF DU REGIME CLASSIQUE", c);
            }
        }
    }

    /** Colonnes communes aux régimes type service/prestation (Classique, BSF, CEC, Ndongo). */
    private void ajouterColonnesService(List<Col> c, double largeurPrenom, double largeurMatricule, String labelMatricule) {
        c.add(new Col("Prénom(s) *", largeurPrenom, p -> nz(p.getPrenom()), false));
        c.add(new Col("Nom *", 6.63, p -> nz(p.getNom()), false));
        c.add(new Col("Date de naissance *", 15.38, p -> date(p.getDateNaissance()), false));
        c.add(new Col("Sexe (H/F) *", 9.88, PatientExportService::sexeHF, false));
        c.add(new Col("Adresse (optionnel)", 15.38, p -> nz(p.getAdresse()), false));
        c.add(new Col("N° Tél (optionnel)", 13.75, p -> nz(p.getTelephone()), false));
        c.add(new Col(labelMatricule, largeurMatricule, p -> nz(p.getNumeroMatricule()), false));
        c.add(new Col("Date de prise en charge *", 19.25, p -> date(p.getDatePriseEnCharge()), false));
        c.add(new Col("Service *", 7.5, p -> nz(p.getService()), false));
        c.add(new Col("Prestation(s)", 10.5, p -> nz(p.getPrestationMedicament()), false));
        c.add(new Col("Quantité", 7.13, Patient::getQuantite, true));
        c.add(new Col("P.U", 3.25, Patient::getPrixUnitaire, true));
        c.add(new Col("Montant facturé à la SEN-CSU", 22.88, Patient::getMontantTotal, true));
    }

    /** Détermine le mois/année à afficher à partir de la date d'enregistrement des patients. */
    private LocalDate periodeReference(List<Patient> patients) {
        return patients.stream()
                .map(Patient::getDateEnregistrement)
                .filter(java.util.Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Export Excel — réplique de la feuille du modèle officiel
    // ──────────────────────────────────────────────────────────────────────
    public void exportExcel(List<Patient> patients, String categorie, HttpServletResponse response) {
        try {
            String label = categorieLabel(categorie);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=patients-" +
                    (categorie == null ? "tous" : categorie) + ".xlsx");

            Modele m = modele(categorie);
            int ncols = m.colonnes.size();

            XSSFWorkbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet(label.length() > 31 ? label.substring(0, 31) : label);
            sheet.setDisplayGridlines(false);

            // ── Styles ──
            org.apache.poi.ss.usermodel.Font fEntete = workbook.createFont();
            fEntete.setFontName("Arial"); fEntete.setBold(true);
            CellStyle stEntete = workbook.createCellStyle(); stEntete.setFont(fEntete);

            org.apache.poi.ss.usermodel.Font fNormal = workbook.createFont();
            fNormal.setFontName("Arial");
            CellStyle stNormal = workbook.createCellStyle(); stNormal.setFont(fNormal);

            org.apache.poi.ss.usermodel.Font fTitre = workbook.createFont();
            fTitre.setFontName("Arial"); fTitre.setBold(true);
            fTitre.setFontHeightInPoints((short) 14);
            fTitre.setColor(IndexedColors.WHITE.getIndex());
            CellStyle stTitre = workbook.createCellStyle();
            stTitre.setFont(fTitre);
            stTitre.setAlignment(HorizontalAlignment.CENTER);
            stTitre.setVerticalAlignment(VerticalAlignment.CENTER);
            stTitre.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xE2, (byte) 0x4A, (byte) 0x4A}, null));
            stTitre.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            org.apache.poi.ss.usermodel.Font fHeader = workbook.createFont();
            fHeader.setFontName("Arial"); fHeader.setBold(true);
            CellStyle stHeader = workbook.createCellStyle();
            stHeader.setFont(fHeader);
            stHeader.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xF3, (byte) 0xF3, (byte) 0xF3}, null));
            stHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            stHeader.setWrapText(true);
            stHeader.setVerticalAlignment(VerticalAlignment.CENTER);
            stHeader.setBorderTop(BorderStyle.THIN);
            stHeader.setBorderBottom(BorderStyle.THIN);
            stHeader.setBorderLeft(BorderStyle.THIN);
            stHeader.setBorderRight(BorderStyle.THIN);

            CellStyle stData = workbook.createCellStyle();
            stData.setFont(fNormal);
            stData.setBorderTop(BorderStyle.THIN);
            stData.setBorderBottom(BorderStyle.THIN);
            stData.setBorderLeft(BorderStyle.THIN);
            stData.setBorderRight(BorderStyle.THIN);

            // ── Largeurs de colonnes ──
            for (int i = 0; i < ncols; i++) {
                sheet.setColumnWidth(i, (int) Math.round(m.colonnes.get(i).width * 256));
            }

            // ── Ligne 1 : entête structure ──
            Row r1 = sheet.createRow(0);
            Cell c1 = r1.createCell(0); c1.setCellValue("Entête de la structure de santé"); c1.setCellStyle(stEntete);

            // ── Ligne 2 : lieu / date ──
            Row r2 = sheet.createRow(1);
            Cell c2 = r2.createCell(0);
            c2.setCellValue("Lieu : .......................... , le " + LocalDate.now().format(DATE));
            c2.setCellStyle(stNormal);

            // ── Ligne 7 (idx 6) : titre fusionné ──
            Row r7 = sheet.createRow(6);
            Cell cT = r7.createCell(0); cT.setCellValue(m.titre); cT.setCellStyle(stTitre);
            for (int i = 1; i < ncols; i++) r7.createCell(i).setCellStyle(stTitre);
            sheet.addMergedRegion(new CellRangeAddress(6, 6, 0, ncols - 1));

            // ── Ligne 8 (idx 7) : Mois / Année ──
            LocalDate ref = periodeReference(patients);
            Row r8 = sheet.createRow(7);
            Cell c8a = r8.createCell(0); c8a.setCellValue("Mois"); c8a.setCellStyle(stEntete);
            Cell c8b = r8.createCell(1); c8b.setCellValue(MOIS_FR[ref.getMonthValue() - 1]); c8b.setCellStyle(stNormal);
            Cell c8d = r8.createCell(3); c8d.setCellValue("Année"); c8d.setCellStyle(stEntete);
            Cell c8e = r8.createCell(4); c8e.setCellValue(ref.getYear()); c8e.setCellStyle(stNormal);

            // ── Ligne 9 (idx 8) : référence facture ──
            Row r9 = sheet.createRow(8);
            Cell c9 = r9.createCell(0); c9.setCellValue("N° Référence facture…..");

            // ── Ligne 12 (idx 11) : en-têtes de colonnes ──
            Row header = sheet.createRow(11);
            for (int i = 0; i < ncols; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(m.colonnes.get(i).label);
                cell.setCellStyle(stHeader);
            }

            // ── Lignes 13+ : données ──
            int rowIdx = 12;
            for (Patient p : patients) {
                Row row = sheet.createRow(rowIdx++);
                for (int i = 0; i < ncols; i++) {
                    Col col = m.colonnes.get(i);
                    Cell cell = row.createCell(i);
                    Object v = col.value.apply(p);
                    if (col.numeric && v instanceof Number) {
                        cell.setCellValue(((Number) v).doubleValue());
                    } else {
                        cell.setCellValue(v == null ? "" : v.toString());
                    }
                    cell.setCellStyle(stData);
                }
            }

            // ── Volets figés en A13 ──
            sheet.createFreezePane(0, 12);

            workbook.write(response.getOutputStream());
            workbook.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Export PDF — mêmes colonnes que la feuille de la catégorie
    // ──────────────────────────────────────────────────────────────────────
    public void exportPdf(List<Patient> patients, String categorie, HttpServletResponse response) {
        try {
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=patients.pdf");

            Modele m = modele(categorie);
            int ncols = m.colonnes.size();

            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, response.getOutputStream());
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 14, Font.BOLD, Color.WHITE);
            Font subFont = new Font(Font.HELVETICA, 10, Font.ITALIC, Color.DARK_GRAY);
            Font headerFont = new Font(Font.HELVETICA, 8, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 7, Font.NORMAL);

            // Bandeau titre rouge
            PdfPTable titleTable = new PdfPTable(1);
            titleTable.setWidthPercentage(100);
            PdfPCell titleCell = new PdfPCell(new Paragraph(m.titre, titleFont));
            titleCell.setBackgroundColor(new Color(0xE2, 0x4A, 0x4A));
            titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            titleCell.setPadding(8);
            titleTable.addCell(titleCell);
            document.add(titleTable);

            LocalDate ref = periodeReference(patients);
            Paragraph meta = new Paragraph("Mois : " + MOIS_FR[ref.getMonthValue() - 1] + "   Année : " + ref.getYear()
                    + "   —   " + patients.size() + " patient(s)", subFont);
            meta.setAlignment(Element.ALIGN_CENTER);
            meta.setSpacingBefore(4);
            meta.setSpacingAfter(10);
            document.add(meta);

            PdfPTable table = new PdfPTable(ncols);
            table.setWidthPercentage(100);

            for (Col col : m.colonnes) {
                PdfPCell cell = new PdfPCell(new Paragraph(col.label, headerFont));
                cell.setBackgroundColor(new Color(0xF3, 0xF3, 0xF3));
                cell.setPadding(4);
                table.addCell(cell);
            }

            for (Patient p : patients) {
                for (Col col : m.colonnes) {
                    Object v = col.value.apply(p);
                    table.addCell(new PdfPCell(new Paragraph(v == null ? "" : v.toString(), normalFont)));
                }
            }

            document.add(table);
            document.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
