package com.poc.geofence.exception;

public class GeocercaNoEncontradaException extends RuntimeException {

    public GeocercaNoEncontradaException(Long id) {
        super("No existe la geocerca con id " + id);
    }
}
