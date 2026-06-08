package com.csu.gestion_csu.controller;

import com.csu.gestion_csu.model.Patient;
import com.csu.gestion_csu.model.Activite;
import com.csu.gestion_csu.model.Constat;
import com.csu.gestion_csu.model.Enrolement;
import com.csu.gestion_csu.model.Bureau;
import com.csu.gestion_csu.model.Utilisateur;
import com.csu.gestion_csu.repository.PatientRepository;
import com.csu.gestion_csu.repository.ActiviteRepository;
import com.csu.gestion_csu.repository.ConstatRepository;
import com.csu.gestion_csu.repository.EnrolementRepository;
import com.csu.gestion_csu.repository.BureauRepository;
import com.csu.gestion_csu.repository.UtilisateurRepository;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.PdfPCell;
import java.awt.Color;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rapports")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RapportController {

    private final PatientRepository patientRepository;
    private final ActiviteRepository activiteRepository;
    private final ConstatRepository constatRepository;
    private final EnrolementRepository enrolementRepository;
    private final BureauRepository bureauRepository;
    private final UtilisateurRepository utilisateurRepository;

    /**
     * Resolves the effective bureau ID: if explicit param is provided use it,
     * otherwise if the current user is an AGENT, use their bureau.
     */
    private Long resolveEffectiveBureauId(Long explicitBureauId) {
        if (explicitBureauId != null) return explicitBureauId;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Utilisateur) {
            Utilisateur user = (Utilisateur) auth.getPrincipal();
            if ("AGENT".equals(user.getRole())) {
                return user.getBureauId();
            }
        }
        return null;
    }

    /**
     * Gets agents concerned by the report scope.
     * If agentId is specified, returns just that agent.
     * If bureauId is specified, returns all agents from that bureau.
     * Otherwise returns empty list (global report).
     */
    private List<Utilisateur> getReportAgents(Long bureauId, Long agentId) {
        if (agentId != null) {
            return utilisateurRepository.findById(agentId)
                    .map(List::of)
                    .orElse(List.of());
        }
        if (bureauId != null) {
            return utilisateurRepository.findByBureauId(bureauId);
        }
        return List.of();
    }

    @GetMapping("/pdf")
    public void downloadPdf(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(name = "bureau_id", required = false) Long bureauId,
            @RequestParam(name = "structure_id", required = false) Long structureId,
            @RequestParam(name = "created_by", required = false) Long agentId,
            HttpServletResponse response) {
        try {
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=rapport_csu.pdf");

            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59, 999999);

            Long effectiveBureauId = resolveEffectiveBureauId(bureauId);

            List<Patient> patients = patientRepository.findPatientsForReport(start, end, effectiveBureauId, agentId);
            List<Enrolement> enrolements = enrolementRepository.findEnrolementsForReport(start, end, effectiveBureauId, agentId);
            List<Activite> activites = activiteRepository.findActivitesForReport(start, end, effectiveBureauId, agentId);
            List<Constat> constats = constatRepository.findConstatsForReport(start, end, effectiveBureauId, agentId);

            String bureauNom = "Global";
            if (effectiveBureauId != null) {
                bureauNom = bureauRepository.findById(effectiveBureauId).map(Bureau::getNom).orElse("Inconnu");
            }

            // Get agents concerned by this report
            List<Utilisateur> reportAgents = getReportAgents(effectiveBureauId, agentId);

            // Get structure name if applicable
            String structureNom = null;
            if (structureId != null) {
                structureNom = bureauRepository.findById(structureId).map(Bureau::getNom).orElse(null);
            }

            Document document = new Document();
            PdfWriter.getInstance(document, response.getOutputStream());
            document.open();

            // Font styles
            Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(0, 135, 90));
            Font subtitleFont = new Font(Font.HELVETICA, 12, Font.ITALIC, Color.DARK_GRAY);
            Font sectionFont = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(0, 135, 90));
            Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
            Font normalFont = new Font(Font.HELVETICA, 9, Font.NORMAL);
            Font infoLabelFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.DARK_GRAY);
            Font infoValueFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.DARK_GRAY);

            // Title
            Paragraph title = new Paragraph("Rapport d'Activité CSU - " + bureauNom, titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(5);
            document.add(title);

            Paragraph period = new Paragraph("Période : du " + startDate + " au " + endDate, subtitleFont);
            period.setAlignment(Element.ALIGN_CENTER);
            period.setSpacingAfter(10);
            document.add(period);

            // Agent(s) and Structure info section
            if (!reportAgents.isEmpty() || structureNom != null) {
                Paragraph infoTitle = new Paragraph("Informations du rapport", sectionFont);
                infoTitle.setSpacingAfter(8);
                document.add(infoTitle);

                PdfPTable infoTable = new PdfPTable(2);
                infoTable.setWidthPercentage(100);
                infoTable.setWidths(new float[]{1.5f, 4f});
                infoTable.setSpacingAfter(15);

                // Bureau row
                if (effectiveBureauId != null) {
                    PdfPCell labelCell = new PdfPCell(new Paragraph("Bureau CSU :", infoLabelFont));
                    labelCell.setBorder(0);
                    labelCell.setPadding(5);
                    infoTable.addCell(labelCell);
                    PdfPCell valueCell = new PdfPCell(new Paragraph(bureauNom, infoValueFont));
                    valueCell.setBorder(0);
                    valueCell.setPadding(5);
                    infoTable.addCell(valueCell);
                }

                // Structure row
                if (structureNom != null) {
                    PdfPCell labelCell = new PdfPCell(new Paragraph("Structure :", infoLabelFont));
                    labelCell.setBorder(0);
                    labelCell.setPadding(5);
                    infoTable.addCell(labelCell);
                    PdfPCell valueCell = new PdfPCell(new Paragraph(structureNom, infoValueFont));
                    valueCell.setBorder(0);
                    valueCell.setPadding(5);
                    infoTable.addCell(valueCell);
                }

                // Agent(s) row
                if (!reportAgents.isEmpty()) {
                    PdfPCell labelCell = new PdfPCell(new Paragraph("Agent(s) :", infoLabelFont));
                    labelCell.setBorder(0);
                    labelCell.setPadding(5);
                    infoTable.addCell(labelCell);

                    String agentNames = reportAgents.stream()
                            .map(a -> a.getPrenom() + " " + a.getNom() + " (" + a.getRole() + ")")
                            .collect(Collectors.joining(", "));
                    PdfPCell valueCell = new PdfPCell(new Paragraph(agentNames, infoValueFont));
                    valueCell.setBorder(0);
                    valueCell.setPadding(5);
                    infoTable.addCell(valueCell);
                }

                document.add(infoTable);
            }

            // Summary Table / KPIs
            Paragraph kpiTitle = new Paragraph("Résumé des indicateurs", sectionFont);
            kpiTitle.setSpacingAfter(10);
            document.add(kpiTitle);

            PdfPTable kpiTable = new PdfPTable(4);
            kpiTable.setWidthPercentage(100);
            kpiTable.setSpacingAfter(20);

            String[] kpiHeaders = {"Nouveaux Patients", "Enrôlements", "Activités Terrain", "Constats Signalés"};
            for (String h : kpiHeaders) {
                PdfPCell cell = new PdfPCell(new Paragraph(h, headerFont));
                cell.setBackgroundColor(new Color(0, 135, 90));
                cell.setPadding(8);
                kpiTable.addCell(cell);
            }
            kpiTable.addCell(new PdfPCell(new Paragraph(String.valueOf(patients.size()), normalFont)));
            kpiTable.addCell(new PdfPCell(new Paragraph(String.valueOf(enrolements.size()), normalFont)));
            kpiTable.addCell(new PdfPCell(new Paragraph(String.valueOf(activites.size()), normalFont)));
            kpiTable.addCell(new PdfPCell(new Paragraph(String.valueOf(constats.size()), normalFont)));
            document.add(kpiTable);

            // 1. Section Patients
            Paragraph pTitle = new Paragraph("1. Nouveaux Patients", sectionFont);
            pTitle.setSpacingAfter(10);
            document.add(pTitle);

            PdfPTable pTable = new PdfPTable(5);
            pTable.setWidthPercentage(100);
            pTable.setSpacingAfter(20);
            pTable.setWidths(new float[]{1.5f, 2f, 2f, 1.5f, 3f});
            String[] pHeaders = {"N° Dossier", "Nom", "Prénom", "Téléphone", "Commune"};
            for (String h : pHeaders) {
                PdfPCell cell = new PdfPCell(new Paragraph(h, headerFont));
                cell.setBackgroundColor(new Color(0, 135, 90));
                cell.setPadding(6);
                pTable.addCell(cell);
            }
            for (Patient p : patients) {
                pTable.addCell(new PdfPCell(new Paragraph(p.getNumeroDossier() != null ? p.getNumeroDossier() : "", normalFont)));
                pTable.addCell(new PdfPCell(new Paragraph(p.getNom() != null ? p.getNom() : "", normalFont)));
                pTable.addCell(new PdfPCell(new Paragraph(p.getPrenom() != null ? p.getPrenom() : "", normalFont)));
                pTable.addCell(new PdfPCell(new Paragraph(p.getTelephone() != null ? p.getTelephone() : "", normalFont)));
                pTable.addCell(new PdfPCell(new Paragraph(p.getCommune() != null ? p.getCommune() : "", normalFont)));
            }
            document.add(pTable);

            // 2. Section Enrôlements
            Paragraph eTitle = new Paragraph("2. Bénéficiaires Enrôlés", sectionFont);
            eTitle.setSpacingAfter(10);
            document.add(eTitle);

            PdfPTable eTable = new PdfPTable(4);
            eTable.setWidthPercentage(100);
            eTable.setSpacingAfter(20);
            String[] eHeaders = {"N° Bénéficiaire", "Patient", "Date Enrôlement", "Statut"};
            for (String h : eHeaders) {
                PdfPCell cell = new PdfPCell(new Paragraph(h, headerFont));
                cell.setBackgroundColor(new Color(0, 135, 90));
                cell.setPadding(6);
                eTable.addCell(cell);
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            for (Enrolement e : enrolements) {
                eTable.addCell(new PdfPCell(new Paragraph(e.getNumeroBeneficiaire() != null ? e.getNumeroBeneficiaire() : "", normalFont)));
                String pName = (e.getPrenom() != null || e.getNom() != null)
                        ? ((e.getPrenom() == null ? "" : e.getPrenom()) + " " + (e.getNom() == null ? "" : e.getNom())).trim()
                        : (e.getPatient() != null ? (e.getPatient().getPrenom() + " " + e.getPatient().getNom()) : "Inconnu");
                eTable.addCell(new PdfPCell(new Paragraph(pName, normalFont)));
                eTable.addCell(new PdfPCell(new Paragraph(e.getDateEnrolement() != null ? e.getDateEnrolement().format(formatter) : "", normalFont)));
                eTable.addCell(new PdfPCell(new Paragraph(e.getStatut() != null ? e.getStatut() : "", normalFont)));
            }
            document.add(eTable);

            // 3. Section Activités
            Paragraph aTitle = new Paragraph("3. Activités et Sensibilisations", sectionFont);
            aTitle.setSpacingAfter(10);
            document.add(aTitle);

            PdfPTable aTable = new PdfPTable(4);
            aTable.setWidthPercentage(100);
            aTable.setSpacingAfter(20);
            String[] aHeaders = {"Type", "Description", "Date", "Participants"};
            for (String h : aHeaders) {
                PdfPCell cell = new PdfPCell(new Paragraph(h, headerFont));
                cell.setBackgroundColor(new Color(0, 135, 90));
                cell.setPadding(6);
                aTable.addCell(cell);
            }
            for (Activite a : activites) {
                aTable.addCell(new PdfPCell(new Paragraph(a.getTypeActivite() != null ? a.getTypeActivite() : "", normalFont)));
                aTable.addCell(new PdfPCell(new Paragraph(a.getDescription() != null ? a.getDescription() : "", normalFont)));
                aTable.addCell(new PdfPCell(new Paragraph(a.getDateActivite() != null ? a.getDateActivite().format(formatter) : "", normalFont)));
                aTable.addCell(new PdfPCell(new Paragraph(String.valueOf(a.getNombreParticipants()), normalFont)));
            }
            document.add(aTable);

            // 4. Section Constats
            Paragraph cTitle = new Paragraph("4. Constats et Incidents", sectionFont);
            cTitle.setSpacingAfter(10);
            document.add(cTitle);

            PdfPTable cTable = new PdfPTable(4);
            cTable.setWidthPercentage(100);
            cTable.setSpacingAfter(20);
            String[] cHeaders = {"Référence", "Description", "Gravité", "Statut"};
            for (String h : cHeaders) {
                PdfPCell cell = new PdfPCell(new Paragraph(h, headerFont));
                cell.setBackgroundColor(new Color(0, 135, 90));
                cell.setPadding(6);
                cTable.addCell(cell);
            }
            for (Constat c : constats) {
                cTable.addCell(new PdfPCell(new Paragraph(c.getReferenceConstat() != null ? c.getReferenceConstat() : "", normalFont)));
                cTable.addCell(new PdfPCell(new Paragraph(c.getDescription() != null ? c.getDescription() : "", normalFont)));
                cTable.addCell(new PdfPCell(new Paragraph(c.getPriorite() != null ? c.getPriorite() : "", normalFont)));
                cTable.addCell(new PdfPCell(new Paragraph(c.getStatut() != null ? c.getStatut() : "", normalFont)));
            }
            document.add(cTable);

            document.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @GetMapping("/excel")
    public void downloadExcel(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(name = "bureau_id", required = false) Long bureauId,
            @RequestParam(name = "structure_id", required = false) Long structureId,
            @RequestParam(name = "created_by", required = false) Long agentId,
            HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=rapport_csu.xlsx");

            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59, 999999);

            Long effectiveBureauId = resolveEffectiveBureauId(bureauId);

            List<Patient> patients = patientRepository.findPatientsForReport(start, end, effectiveBureauId, agentId);
            List<Enrolement> enrolements = enrolementRepository.findEnrolementsForReport(start, end, effectiveBureauId, agentId);
            List<Activite> activites = activiteRepository.findActivitesForReport(start, end, effectiveBureauId, agentId);
            List<Constat> constats = constatRepository.findConstatsForReport(start, end, effectiveBureauId, agentId);

            // Get agents concerned by this report
            List<Utilisateur> reportAgents = getReportAgents(effectiveBureauId, agentId);

            String bureauNom = "Global";
            if (effectiveBureauId != null) {
                bureauNom = bureauRepository.findById(effectiveBureauId).map(Bureau::getNom).orElse("Inconnu");
            }

            // Get structure name if applicable
            String structureNom = null;
            if (structureId != null) {
                structureNom = bureauRepository.findById(structureId).map(Bureau::getNom).orElse(null);
            }

            Workbook workbook = new XSSFWorkbook();

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFontExcel = workbook.createFont();
            headerFontExcel.setBold(true);
            headerFontExcel.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFontExcel);
            headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Title Style
            CellStyle titleStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font titleFontExcel = workbook.createFont();
            titleFontExcel.setBold(true);
            titleFontExcel.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFontExcel);

            // Info Label Style
            CellStyle infoLabelStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font infoLabelFontExcel = workbook.createFont();
            infoLabelFontExcel.setBold(true);
            infoLabelStyle.setFont(infoLabelFontExcel);


            // 1. Sheet Résumé
            Sheet summarySheet = workbook.createSheet("Résumé");
            Row titleRow = summarySheet.createRow(0);
            titleRow.createCell(0).setCellValue("Résumé Opérationnel CSU - " + bureauNom);
            titleRow.getCell(0).setCellStyle(titleStyle);

            Row periodRow = summarySheet.createRow(1);
            periodRow.createCell(0).setCellValue("Période : du " + startDate + " au " + endDate);

            // Agent(s) and Structure info
            int infoRowIdx = 3;

            if (effectiveBureauId != null) {
                Row bureauRow = summarySheet.createRow(infoRowIdx++);
                Cell blabel = bureauRow.createCell(0);
                blabel.setCellValue("Bureau CSU :");
                blabel.setCellStyle(infoLabelStyle);
                bureauRow.createCell(1).setCellValue(bureauNom);
            }

            if (structureNom != null) {
                Row structRow = summarySheet.createRow(infoRowIdx++);
                Cell slabel = structRow.createCell(0);
                slabel.setCellValue("Structure :");
                slabel.setCellStyle(infoLabelStyle);
                structRow.createCell(1).setCellValue(structureNom);
            }

            if (!reportAgents.isEmpty()) {
                Row agentHeaderRow = summarySheet.createRow(infoRowIdx++);
                Cell alabel = agentHeaderRow.createCell(0);
                alabel.setCellValue("Agent(s) :");
                alabel.setCellStyle(infoLabelStyle);

                String agentNames = reportAgents.stream()
                        .map(a -> a.getPrenom() + " " + a.getNom() + " (" + a.getRole() + ")")
                        .collect(Collectors.joining(", "));
                agentHeaderRow.createCell(1).setCellValue(agentNames);
            }

            // KPI section
            infoRowIdx++; // blank row separator
            String[] kpis = {"Indicateur", "Valeur"};
            Row kpiHeader = summarySheet.createRow(infoRowIdx++);
            for (int i = 0; i < kpis.length; i++) {
                Cell c = kpiHeader.createCell(i);
                c.setCellValue(kpis[i]);
                c.setCellStyle(headerStyle);
            }

            String[][] kpiData = {
                {"Nouveaux Patients Enregistrés", String.valueOf(patients.size())},
                {"Bénéficiaires Enrôlés", String.valueOf(enrolements.size())},
                {"Activités Réalisées", String.valueOf(activites.size())},
                {"Constats Signalés", String.valueOf(constats.size())}
            };
            for (String[] data : kpiData) {
                Row row = summarySheet.createRow(infoRowIdx++);
                row.createCell(0).setCellValue(data[0]);
                row.createCell(1).setCellValue(data[1]);
            }
            summarySheet.autoSizeColumn(0);
            summarySheet.autoSizeColumn(1);

            // 2. Sheet Agents (NEW - lists agents and their structure)
            if (!reportAgents.isEmpty()) {
                Sheet agentSheet = workbook.createSheet("Agents");
                Row agentHeader = agentSheet.createRow(0);
                String[] agentHeaders = {"ID", "Nom", "Prénom", "Rôle", "Username", "Email", "Téléphone", "Bureau", "Structure"};
                for (int i = 0; i < agentHeaders.length; i++) {
                    Cell cell = agentHeader.createCell(i);
                    cell.setCellValue(agentHeaders[i]);
                    cell.setCellStyle(headerStyle);
                }
                int agentRowIdx = 1;
                for (Utilisateur agent : reportAgents) {
                    Row row = agentSheet.createRow(agentRowIdx++);
                    row.createCell(0).setCellValue(agent.getId());
                    row.createCell(1).setCellValue(agent.getNom() != null ? agent.getNom() : "");
                    row.createCell(2).setCellValue(agent.getPrenom() != null ? agent.getPrenom() : "");
                    row.createCell(3).setCellValue(agent.getRole() != null ? agent.getRole() : "");
                    row.createCell(4).setCellValue(agent.getUsername() != null ? agent.getUsername() : "");
                    row.createCell(5).setCellValue(agent.getEmail() != null ? agent.getEmail() : "");
                    row.createCell(6).setCellValue(agent.getTelephone() != null ? agent.getTelephone() : "");
                    // Resolve bureau name
                    String agentBureauNom = "";
                    if (agent.getBureauId() != null) {
                        agentBureauNom = bureauRepository.findById(agent.getBureauId())
                                .map(Bureau::getNom).orElse("");
                    }
                    row.createCell(7).setCellValue(agentBureauNom);
                    // Resolve structure name
                    String agentStructureNom = "";
                    if (agent.getStructureId() != null) {
                        agentStructureNom = bureauRepository.findById(agent.getStructureId())
                                .map(Bureau::getNom).orElse("");
                    }
                    row.createCell(8).setCellValue(agentStructureNom);
                }
                for (int i = 0; i < agentHeaders.length; i++) agentSheet.autoSizeColumn(i);
            }

            // 3. Sheet Patients
            Sheet patientSheet = workbook.createSheet("Patients");
            Row pHeader = patientSheet.createRow(0);
            String[] pHeaders = {"ID", "N. Dossier", "Nom", "Prénom", "Téléphone", "Commune", "Région", "Département"};
            for (int i = 0; i < pHeaders.length; i++) {
                Cell cell = pHeader.createCell(i);
                cell.setCellValue(pHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            int rowIdx = 1;
            for (Patient p : patients) {
                Row row = patientSheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(p.getId());
                row.createCell(1).setCellValue(p.getNumeroDossier() != null ? p.getNumeroDossier() : "");
                row.createCell(2).setCellValue(p.getNom() != null ? p.getNom() : "");
                row.createCell(3).setCellValue(p.getPrenom() != null ? p.getPrenom() : "");
                row.createCell(4).setCellValue(p.getTelephone() != null ? p.getTelephone() : "");
                row.createCell(5).setCellValue(p.getCommune() != null ? p.getCommune() : "");
                row.createCell(6).setCellValue(p.getRegion() != null ? p.getRegion() : "");
                row.createCell(7).setCellValue(p.getDepartement() != null ? p.getDepartement() : "");
            }
            for (int i = 0; i < pHeaders.length; i++) patientSheet.autoSizeColumn(i);

            // 4. Sheet Enrôlements
            Sheet enrSheet = workbook.createSheet("Enrôlements");
            Row eHeader = enrSheet.createRow(0);
            String[] eHeaders = {"ID", "N° Bénéficiaire", "Patient", "Date Enrôlement", "Statut"};
            for (int i = 0; i < eHeaders.length; i++) {
                Cell cell = eHeader.createCell(i);
                cell.setCellValue(eHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            rowIdx = 1;
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            for (Enrolement e : enrolements) {
                Row row = enrSheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(e.getId());
                row.createCell(1).setCellValue(e.getNumeroBeneficiaire() != null ? e.getNumeroBeneficiaire() : "");
                String pName = (e.getPrenom() != null || e.getNom() != null)
                        ? ((e.getPrenom() == null ? "" : e.getPrenom()) + " " + (e.getNom() == null ? "" : e.getNom())).trim()
                        : (e.getPatient() != null ? (e.getPatient().getPrenom() + " " + e.getPatient().getNom()) : "Inconnu");
                row.createCell(2).setCellValue(pName);
                row.createCell(3).setCellValue(e.getDateEnrolement() != null ? e.getDateEnrolement().format(dateFormatter) : "");
                row.createCell(4).setCellValue(e.getStatut() != null ? e.getStatut() : "");
            }
            for (int i = 0; i < eHeaders.length; i++) enrSheet.autoSizeColumn(i);

            // 5. Sheet Activités
            Sheet actSheet = workbook.createSheet("Activités");
            Row aHeader = actSheet.createRow(0);
            String[] aHeaders = {"ID", "Type Activité", "Description", "Date Activité", "Participants"};
            for (int i = 0; i < aHeaders.length; i++) {
                Cell cell = aHeader.createCell(i);
                cell.setCellValue(aHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            rowIdx = 1;
            for (Activite a : activites) {
                Row row = actSheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(a.getId());
                row.createCell(1).setCellValue(a.getTypeActivite() != null ? a.getTypeActivite() : "");
                row.createCell(2).setCellValue(a.getDescription() != null ? a.getDescription() : "");
                row.createCell(3).setCellValue(a.getDateActivite() != null ? a.getDateActivite().format(dateFormatter) : "");
                row.createCell(4).setCellValue(a.getNombreParticipants());
            }
            for (int i = 0; i < aHeaders.length; i++) actSheet.autoSizeColumn(i);

            // 6. Sheet Constats
            Sheet cstSheet = workbook.createSheet("Constats");
            Row cHeader = cstSheet.createRow(0);
            String[] cHeaders = {"ID", "Référence", "Date Constat", "Description", "Priorité", "Statut"};
            for (int i = 0; i < cHeaders.length; i++) {
                Cell cell = cHeader.createCell(i);
                cell.setCellValue(cHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            rowIdx = 1;
            for (Constat c : constats) {
                Row row = cstSheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(c.getId());
                row.createCell(1).setCellValue(c.getReferenceConstat() != null ? c.getReferenceConstat() : "");
                row.createCell(2).setCellValue(c.getDateConstat() != null ? c.getDateConstat().format(dateFormatter) : "");
                row.createCell(3).setCellValue(c.getDescription() != null ? c.getDescription() : "");
                row.createCell(4).setCellValue(c.getPriorite() != null ? c.getPriorite() : "");
                row.createCell(5).setCellValue(c.getStatut() != null ? c.getStatut() : "");
            }
            for (int i = 0; i < cHeaders.length; i++) cstSheet.autoSizeColumn(i);

            workbook.write(response.getOutputStream());
            workbook.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Synthèse JSON des indicateurs de la période (pour l'aperçu à l'écran et le
     * widget du tableau de bord) : totaux, répartitions par statut, série
     * journalière et classement par agent.
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(name = "bureau_id", required = false) Long bureauId,
            @RequestParam(name = "created_by", required = false) Long agentId) {

        LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59, 999999);
        Long effectiveBureauId = resolveEffectiveBureauId(bureauId);

        List<Patient> patients = patientRepository.findPatientsForReport(start, end, effectiveBureauId, agentId);
        List<Enrolement> enrolements = enrolementRepository.findEnrolementsForReport(start, end, effectiveBureauId, agentId);
        List<Activite> activites = activiteRepository.findActivitesForReport(start, end, effectiveBureauId, agentId);
        List<Constat> constats = constatRepository.findConstatsForReport(start, end, effectiveBureauId, agentId);

        Map<String, Object> result = new HashMap<>();
        result.put("startDate", startDate);
        result.put("endDate", endDate);
        result.put("bureauNom", effectiveBureauId == null ? "Global"
                : bureauRepository.findById(effectiveBureauId).map(Bureau::getNom).orElse("Inconnu"));

        result.put("totalPatients", patients.size());
        result.put("totalEnrolements", enrolements.size());
        result.put("totalActivites", activites.size());
        result.put("totalConstats", constats.size());
        result.put("totalParticipants", activites.stream()
                .mapToInt(a -> a.getNombreParticipants() == null ? 0 : a.getNombreParticipants()).sum());

        // Répartitions
        result.put("enrolementsParStatut", enrolements.stream()
                .collect(Collectors.groupingBy(e -> e.getStatut() == null ? "INCONNU" : e.getStatut(), Collectors.counting())));
        result.put("constatsParStatut", constats.stream()
                .collect(Collectors.groupingBy(c -> c.getStatut() == null ? "INCONNU" : c.getStatut(), Collectors.counting())));
        result.put("constatsParPriorite", constats.stream()
                .collect(Collectors.groupingBy(c -> c.getPriorite() == null ? "INCONNU" : c.getPriorite(), Collectors.counting())));
        result.put("activitesParType", activites.stream()
                .collect(Collectors.groupingBy(a -> a.getTypeActivite() == null ? "Autre" : a.getTypeActivite(), Collectors.counting())));

        // Série journalière (TreeMap pour l'ordre chronologique)
        Map<String, int[]> parJour = new TreeMap<>();
        for (Patient p : patients) addSerie(parJour, p.getDateEnregistrement(), 0);
        for (Enrolement e : enrolements) addSerie(parJour, e.getDateEnrolement(), 1);
        for (Activite a : activites) addSerie(parJour, a.getDateActivite(), 2);
        for (Constat c : constats) addSerie(parJour, c.getDateConstat(), 3);
        List<Map<String, Object>> serie = parJour.entrySet().stream().map(en -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", en.getKey());
            m.put("patients", en.getValue()[0]);
            m.put("enrolements", en.getValue()[1]);
            m.put("activites", en.getValue()[2]);
            m.put("constats", en.getValue()[3]);
            return m;
        }).collect(Collectors.toList());
        result.put("serie", serie);

        // Classement par agent (sur le périmètre)
        Map<Long, long[]> parAgent = new HashMap<>();
        patients.forEach(p -> bumpAgent(parAgent, p.getAgentId(), 0));
        enrolements.forEach(e -> bumpAgent(parAgent, e.getAgentId(), 1));
        activites.forEach(a -> bumpAgent(parAgent, a.getAgentId(), 2));
        constats.forEach(c -> bumpAgent(parAgent, c.getResponsableId(), 3));

        Map<Long, String> nomById = utilisateurRepository.findAll().stream()
                .collect(Collectors.toMap(Utilisateur::getId, u -> u.getPrenom() + " " + u.getNom(), (a, b) -> a));

        List<Map<String, Object>> agents = parAgent.entrySet().stream().map(en -> {
            long[] v = en.getValue();
            long total = v[0] + v[1] + v[2] + v[3];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("agentId", en.getKey());
            m.put("agentNom", nomById.getOrDefault(en.getKey(), "Agent #" + en.getKey()));
            m.put("patients", v[0]);
            m.put("enrolements", v[1]);
            m.put("activites", v[2]);
            m.put("constats", v[3]);
            m.put("total", total);
            return m;
        }).sorted((x, y) -> Long.compare((Long) y.get("total"), (Long) x.get("total")))
                .collect(Collectors.toList());
        result.put("parAgent", agents);

        return ResponseEntity.ok(result);
    }

    private static void addSerie(Map<String, int[]> map, LocalDateTime dt, int idx) {
        if (dt == null) return;
        String key = dt.toLocalDate().toString();
        int[] arr = map.computeIfAbsent(key, k -> new int[4]);
        arr[idx]++;
    }

    private static void bumpAgent(Map<Long, long[]> map, Long agentId, int idx) {
        if (agentId == null) return;
        long[] arr = map.computeIfAbsent(agentId, k -> new long[4]);
        arr[idx]++;
    }
}
