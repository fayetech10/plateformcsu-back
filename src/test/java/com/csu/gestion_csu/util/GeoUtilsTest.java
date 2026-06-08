package com.csu.gestion_csu.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoUtilsTest {

    @Test
    void distance_memePoint_estZero() {
        double d = GeoUtils.distanceMetres(14.6928, -17.4467, 14.6928, -17.4467);
        assertEquals(0.0, d, 0.001);
    }

    @Test
    void distance_courte_estCoherente() {
        // ~111 m vers le nord (0.001° de latitude ≈ 111 m)
        double d = GeoUtils.distanceMetres(14.6928, -17.4467, 14.6938, -17.4467);
        assertEquals(111.0, d, 5.0);
    }

    @Test
    void distance_dakarThies_environ55km() {
        // Dakar -> Thiès : ~55 km à vol d'oiseau
        double d = GeoUtils.distanceMetres(14.6928, -17.4467, 14.7910, -16.9259);
        assertTrue(d > 50_000 && d < 60_000, "Distance attendue ~55 km, obtenue: " + d);
    }

    @Test
    void horsZone_logique() {
        // Agent à ~111 m, rayon 150 m -> dans la zone
        double proche = GeoUtils.distanceMetres(14.6928, -17.4467, 14.6938, -17.4467);
        assertTrue(proche <= 150);

        // Agent à ~330 m, rayon 150 m -> hors zone
        double loin = GeoUtils.distanceMetres(14.6928, -17.4467, 14.6958, -17.4467);
        assertTrue(loin > 150);
    }
}
