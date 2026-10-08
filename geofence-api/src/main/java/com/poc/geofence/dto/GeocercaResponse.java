package com.poc.geofence.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.JsonNode;

public record GeocercaResponse(
        Long idGeocerca,
        String nombre,
        OffsetDateTime fechaCarga,
        OffsetDateTime fechaActualizacion,
        JsonNode geometria) {
}
