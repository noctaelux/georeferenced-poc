package com.poc.geofence.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para registrar o actualizar una geocerca")
public record GeocercaRequest(
        @Schema(examples = "Zona Centro")
        @NotBlank @Size(max = 150)
        String nombre,

        @Schema(description = "Geometría GeoJSON de tipo Polygon o MultiPolygon en WGS84 (EPSG:4326), "
                + "coordenadas en orden [longitud, latitud]",
                examples = "{\"type\":\"Polygon\",\"coordinates\":[[[-99.14,19.42],[-99.12,19.42],[-99.12,19.44],[-99.14,19.44],[-99.14,19.42]]]}")
        @NotNull
        JsonNode geometria) {
}
