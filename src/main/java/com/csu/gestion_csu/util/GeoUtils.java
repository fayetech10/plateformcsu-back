package com.csu.gestion_csu.util;

/**
 * Utilitaires de géolocalisation.
 */
public final class GeoUtils {

    private static final double RAYON_TERRE_METRES = 6_371_000.0;

    private GeoUtils() {}

    /**
     * Distance en mètres entre deux points GPS (formule de Haversine).
     */
    public static double distanceMetres(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return RAYON_TERRE_METRES * c;
    }
}
