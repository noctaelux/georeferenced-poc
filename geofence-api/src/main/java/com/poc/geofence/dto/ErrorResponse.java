package com.poc.geofence.dto;

import java.util.List;

public record ErrorResponse(int status, String mensaje, List<String> detalles) {

    public ErrorResponse(int status, String mensaje) {
        this(status, mensaje, List.of());
    }
}
