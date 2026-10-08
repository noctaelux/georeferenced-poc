package com.poc.geofence.service;

import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.geofence.dto.GeocercaRequest;
import com.poc.geofence.dto.GeocercaResponse;
import com.poc.geofence.dto.ValidacionRequest;
import com.poc.geofence.dto.ValidacionResponse;
import com.poc.geofence.entity.Geocerca;
import com.poc.geofence.exception.GeocercaNoEncontradaException;
import com.poc.geofence.exception.GeometriaInvalidaException;
import com.poc.geofence.repository.GeocercaRepository;
import com.poc.geofence.repository.GeocercaRepository.AnalisisGeometria;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class GeocercaService {

    static final int SRID_WGS84 = 4326;
    private static final Set<String> TIPOS_PERMITIDOS = Set.of("POLYGON", "MULTIPOLYGON");

    private final GeocercaRepository repository;
    private final ObjectMapper mapper;

    public GeocercaService(GeocercaRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public GeocercaResponse recuperar(Long id) {
        return aResponse(buscar(id));
    }

    @Transactional
    public GeocercaResponse registrar(GeocercaRequest request) {
        String geoJson = validarGeometria(request.geometria());
        Geocerca geocerca = new Geocerca();
        geocerca.nombre = request.nombre().trim();
        geocerca.geometria = geoJson;
        repository.persistAndFlush(geocerca);
        return aResponse(geocerca);
    }

    @Transactional
    public GeocercaResponse actualizar(Long id, GeocercaRequest request) {
        String geoJson = validarGeometria(request.geometria());
        Geocerca geocerca = buscar(id);
        geocerca.nombre = request.nombre().trim();
        geocerca.geometria = geoJson;
        repository.flush();
        return aResponse(geocerca);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!repository.deleteById(id)) {
            throw new GeocercaNoEncontradaException(id);
        }
    }

    public ValidacionResponse validar(ValidacionRequest request) {
        var coincidencias = repository.buscarQueContienen(request.latitud(), request.longitud());
        return new ValidacionResponse(request.latitud(), request.longitud(),
                !coincidencias.isEmpty(), coincidencias);
    }

    private Geocerca buscar(Long id) {
        return repository.findByIdOptional(id)
                .orElseThrow(() -> new GeocercaNoEncontradaException(id));
    }

    /**
     * Delega en PostGIS la interpretación del GeoJSON y verifica tipo, SRID y validez topológica.
     *
     * @return el GeoJSON listo para persistir
     */
    private String validarGeometria(JsonNode geometria) {
        if (!geometria.isObject()) {
            throw new GeometriaInvalidaException("La geometría debe ser un objeto GeoJSON");
        }
        String geoJson = geometria.toString();
        AnalisisGeometria analisis;
        try {
            analisis = repository.analizar(geoJson);
        } catch (PersistenceException e) {
            throw new GeometriaInvalidaException("La geometría no es un GeoJSON válido");
        }
        if (!TIPOS_PERMITIDOS.contains(analisis.tipo())) {
            throw new GeometriaInvalidaException(
                    "La geometría debe ser Polygon o MultiPolygon, se recibió " + analisis.tipo());
        }
        if (analisis.srid() != 0 && analisis.srid() != SRID_WGS84) {
            throw new GeometriaInvalidaException(
                    "La geometría debe estar en EPSG:4326, se recibió EPSG:" + analisis.srid());
        }
        if (!analisis.coordenadasEnRango()) {
            throw new GeometriaInvalidaException(
                    "Coordenadas fuera de rango: la longitud debe estar entre -180 y 180 y la latitud "
                            + "entre -90 y 90. Verifica que el orden sea [longitud, latitud]");
        }
        if (!analisis.valida()) {
            throw new GeometriaInvalidaException("La geometría no es válida: " + analisis.motivo());
        }
        return geoJson;
    }

    private GeocercaResponse aResponse(Geocerca g) {
        return new GeocercaResponse(g.idGeocerca, g.nombre, g.fechaCarga, g.fechaActualizacion,
                aJson(g.geometria));
    }

    private JsonNode aJson(String geoJson) {
        try {
            return mapper.readTree(geoJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("GeoJSON almacenado no interpretable", e);
        }
    }
}
