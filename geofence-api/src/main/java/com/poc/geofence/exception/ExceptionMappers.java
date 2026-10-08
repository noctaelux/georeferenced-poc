package com.poc.geofence.exception;

import java.util.List;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import com.poc.geofence.dto.ErrorResponse;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;

/** Traduce las excepciones de la aplicación a respuestas HTTP con un formato uniforme. */
public class ExceptionMappers {

    @ServerExceptionMapper
    public Response noEncontrada(GeocercaNoEncontradaException e) {
        return error(Response.Status.NOT_FOUND, e.getMessage(), List.of());
    }

    @ServerExceptionMapper
    public Response geometriaInvalida(GeometriaInvalidaException e) {
        return error(Response.Status.BAD_REQUEST, e.getMessage(), List.of());
    }

    @ServerExceptionMapper
    public Response validacion(ConstraintViolationException e) {
        List<String> detalles = e.getConstraintViolations().stream()
                .map(v -> ultimoNodo(v.getPropertyPath().toString()) + ": " + v.getMessage())
                .sorted()
                .toList();
        return error(Response.Status.BAD_REQUEST, "La solicitud no es válida", detalles);
    }

    private static Response error(Response.Status status, String mensaje, List<String> detalles) {
        return Response.status(status)
                .entity(new ErrorResponse(status.getStatusCode(), mensaje, detalles))
                .build();
    }

    /** "registrar.request.nombre" -> "nombre" */
    private static String ultimoNodo(String ruta) {
        return ruta.substring(ruta.lastIndexOf('.') + 1);
    }
}
