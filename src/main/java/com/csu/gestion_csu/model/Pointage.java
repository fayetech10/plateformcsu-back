package com.csu.gestion_csu.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pointage de présence d'un agent : une ligne par agent et par jour.
 * heureArrivee est renseignée au pointage d'arrivée, heureDepart au départ.
 */
@Entity
@Table(name = "pointages", indexes = {
        @Index(name = "idx_pointage_agent_date", columnList = "agentId,datePointage"),
        @Index(name = "idx_pointage_date", columnList = "datePointage"),
        @Index(name = "idx_pointage_bureau", columnList = "bureauId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pointage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long agentId;

    private Long bureauId;

    @Column(nullable = false)
    private LocalDate datePointage;

    @Column(nullable = false)
    private LocalDateTime heureArrivee;

    private LocalDateTime heureDepart;

    // Géolocalisation au moment du pointage d'arrivée
    private Double latitude;
    private Double longitude;
    private Double precision;        // précision GPS en mètres (accuracy)
    private Double distanceMetres;   // distance au bureau
    private Boolean horsZone;        // true si hors du rayon de tolérance
    private Boolean positionVerifiee; // false si position indisponible/refusée

    // Géolocalisation au moment du pointage de départ
    private Double latitudeDepart;
    private Double longitudeDepart;
    private Double precisionDepart;
    private Double distanceMetresDepart;
    private Boolean horsZoneDepart;
    private Boolean positionVerifieeDepart;
}
