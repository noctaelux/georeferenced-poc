package com.poc.geofence.entity;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import org.hibernate.annotations.ColumnTransformer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Geocerca almacenada en PostGIS.
 * <p>
 * La columna {@code geometria} es de tipo {@code geometry(Geometry, 4326)} en la base de datos.
 * En Java se maneja como GeoJSON (texto): PostGIS hace la conversión al leer y escribir, por lo que
 * no se requiere Hibernate Spatial ni JTS.
 */
@Entity
@Table(name = "geocercas")
public class Geocerca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_geocerca")
    public Long idGeocerca;

    @Column(name = "nombre", nullable = false, length = 150)
    public String nombre;

    @Column(name = "fecha_carga", nullable = false, updatable = false)
    public OffsetDateTime fechaCarga;

    @Column(name = "fecha_actualizacion", nullable = false)
    public OffsetDateTime fechaActualizacion;

    @ColumnTransformer(
            read = "ST_AsGeoJSON(geometria)",
            write = "ST_SetSRID(ST_GeomFromGeoJSON(CAST(? AS text)), 4326)")
    @Column(name = "geometria", nullable = false)
    public String geometria;

    @PrePersist
    void alRegistrar() {
        fechaCarga = ahora();
        fechaActualizacion = fechaCarga;
    }

    @PreUpdate
    void alActualizar() {
        fechaActualizacion = ahora();
    }

    /** PostgreSQL guarda microsegundos; se trunca para que la respuesta coincida con lo almacenado. */
    private static OffsetDateTime ahora() {
        return OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
