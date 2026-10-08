package com.poc.geofence.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Coordenada WGS84 (EPSG:4326) a validar")
public record ValidacionRequest(
        @Schema(examples = "19.43")
        @NotNull @DecimalMin("-90") @DecimalMax("90")
        Double latitud,

        @Schema(examples = "-99.13")
        @NotNull @DecimalMin("-180") @DecimalMax("180")
        Double longitud) {
}
