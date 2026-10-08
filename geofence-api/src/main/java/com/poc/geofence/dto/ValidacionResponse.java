package com.poc.geofence.dto;

import java.util.List;

import com.poc.geofence.repository.GeocercaRepository.Coincidencia;

public record ValidacionResponse(
        double latitud,
        double longitud,
        boolean dentro,
        List<Coincidencia> geocercas) {
}
