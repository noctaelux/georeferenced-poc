package com.poc.geofence.repository;

import java.util.List;

import com.poc.geofence.entity.Geocerca;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GeocercaRepository implements PanacheRepository<Geocerca> {

    /** Resultado del análisis de una geometría GeoJSON hecho por PostGIS. */
    public record AnalisisGeometria(String tipo, int srid, boolean valida, String motivo,
            double lonMin, double lonMax, double latMin, double latMax) {

        public boolean coordenadasEnRango() {
            return lonMin >= -180 && lonMax <= 180 && latMin >= -90 && latMax <= 90;
        }
    }

    /** Geocerca que contiene un punto. */
    public record Coincidencia(Long idGeocerca, String nombre) {
    }

    /**
     * Pide a PostGIS que interprete el GeoJSON y reporte su tipo, validez topológica y extensión.
     * Lanza una excepción de persistencia si el GeoJSON no es interpretable.
     */
    public AnalisisGeometria analizar(String geoJson) {
        Object[] fila = (Object[]) getEntityManager().createNativeQuery("""
                SELECT GeometryType(g), ST_SRID(g), ST_IsValid(g), ST_IsValidReason(g),
                       ST_XMin(g), ST_XMax(g), ST_YMin(g), ST_YMax(g)
                FROM (SELECT ST_GeomFromGeoJSON(CAST(:geojson AS text)) AS g) AS t
                """)
                .setParameter("geojson", geoJson)
                .getSingleResult();
        return new AnalisisGeometria((String) fila[0], ((Number) fila[1]).intValue(),
                (Boolean) fila[2], (String) fila[3],
                ((Number) fila[4]).doubleValue(), ((Number) fila[5]).doubleValue(),
                ((Number) fila[6]).doubleValue(), ((Number) fila[7]).doubleValue());
    }

    /**
     * Geocercas que cubren el punto (incluye puntos sobre el borde).
     * ST_Covers aprovecha el índice GIST de la columna geometria.
     */
    @SuppressWarnings("unchecked")
    public List<Coincidencia> buscarQueContienen(double latitud, double longitud) {
        List<Object[]> filas = getEntityManager().createNativeQuery("""
                SELECT id_geocerca, nombre
                FROM geocercas
                WHERE ST_Covers(geometria, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326))
                ORDER BY id_geocerca
                """)
                .setParameter("lon", longitud)
                .setParameter("lat", latitud)
                .getResultList();
        return filas.stream()
                .map(f -> new Coincidencia(((Number) f[0]).longValue(), (String) f[1]))
                .toList();
    }
}
