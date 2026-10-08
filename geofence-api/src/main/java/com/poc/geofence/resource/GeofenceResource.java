package com.poc.geofence.resource;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.poc.geofence.dto.GeocercaRequest;
import com.poc.geofence.dto.GeocercaResponse;
import com.poc.geofence.dto.ValidacionRequest;
import com.poc.geofence.dto.ValidacionResponse;
import com.poc.geofence.service.GeocercaService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/geofence")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Geocercas")
public class GeofenceResource {

    private final GeocercaService service;

    public GeofenceResource(GeocercaService service) {
        this.service = service;
    }

    @GET
    @Path("/retrieve/{id}")
    @Operation(summary = "Recupera una geocerca")
    @APIResponse(responseCode = "200", description = "Geocerca encontrada")
    @APIResponse(responseCode = "404", description = "La geocerca no existe")
    public GeocercaResponse retrieve(@PathParam("id") Long id) {
        return service.recuperar(id);
    }

    @POST
    @Path("/register")
    @Operation(summary = "Registra una geocerca")
    @APIResponse(responseCode = "201", description = "Geocerca registrada")
    @APIResponse(responseCode = "400", description = "Datos o geometría inválidos")
    public Response register(@NotNull @Valid GeocercaRequest request) {
        return Response.status(Response.Status.CREATED)
                .entity(service.registrar(request))
                .build();
    }

    @PUT
    @Path("/update/{id}")
    @Operation(summary = "Actualiza el nombre y la geometría de una geocerca")
    @APIResponse(responseCode = "200", description = "Geocerca actualizada")
    @APIResponse(responseCode = "400", description = "Datos o geometría inválidos")
    @APIResponse(responseCode = "404", description = "La geocerca no existe")
    public GeocercaResponse update(@PathParam("id") Long id, @NotNull @Valid GeocercaRequest request) {
        return service.actualizar(id, request);
    }

    @POST
    @Path("/validate")
    @Operation(summary = "Valida si una coordenada se encuentra al interior de alguna geocerca",
            description = "Los puntos ubicados exactamente sobre el borde se consideran dentro.")
    @APIResponse(responseCode = "200", description = "Resultado de la validación")
    public ValidacionResponse validate(@NotNull @Valid ValidacionRequest request) {
        return service.validar(request);
    }

    @DELETE
    @Path("/delete/{id}")
    @Operation(summary = "Elimina una geocerca del catálogo")
    @APIResponse(responseCode = "204", description = "Geocerca eliminada")
    @APIResponse(responseCode = "404", description = "La geocerca no existe")
    public void delete(@PathParam("id") Long id) {
        service.eliminar(id);
    }
}
