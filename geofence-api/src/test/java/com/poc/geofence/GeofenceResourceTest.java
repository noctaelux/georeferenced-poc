package com.poc.geofence;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class GeofenceResourceTest {

    private static final String BASE = "/api/v1/geofence";

    /** Cuadro aproximado del Centro Histórico de la CDMX. */
    private static final String POLIGONO_CENTRO = """
            {"type":"Polygon","coordinates":[[[-99.14,19.42],[-99.12,19.42],[-99.12,19.44],[-99.14,19.44],[-99.14,19.42]]]}""";

    /** Cuadro lejano (Monterrey). */
    private static final String POLIGONO_MTY = """
            {"type":"Polygon","coordinates":[[[-100.33,25.66],[-100.30,25.66],[-100.30,25.69],[-100.33,25.69],[-100.33,25.66]]]}""";

    private static String cuerpo(String nombre, String geometria) {
        return "{\"nombre\":\"" + nombre + "\",\"geometria\":" + geometria + "}";
    }

    private static long registrar(String nombre, String geometria) {
        return given().contentType(ContentType.JSON).body(cuerpo(nombre, geometria))
                .when().post(BASE + "/register")
                .then().statusCode(201)
                .extract().jsonPath().getLong("idGeocerca");
    }

    private static void validar(double lat, double lon, long idEsperado, boolean dentro) {
        var respuesta = given().contentType(ContentType.JSON)
                .body("{\"latitud\":" + lat + ",\"longitud\":" + lon + "}")
                .when().post(BASE + "/validate")
                .then().statusCode(200);
        if (dentro) {
            respuesta.body("dentro", equalTo(true))
                    .body("geocercas.idGeocerca", hasItem((int) idEsperado));
        } else {
            respuesta.body("geocercas.idGeocerca", org.hamcrest.Matchers.not(hasItem((int) idEsperado)));
        }
    }

    @Test
    void cicloDeVidaCompleto() {
        long id = registrar("Zona Centro", POLIGONO_CENTRO);

        String fechaCarga = given().when().get(BASE + "/retrieve/" + id)
                .then().statusCode(200)
                .body("nombre", equalTo("Zona Centro"))
                .body("fechaCarga", notNullValue())
                .body("geometria.type", equalTo("Polygon"))
                .body("geometria.coordinates[0][0]", contains(-99.14f, 19.42f))
                .extract().jsonPath().getString("fechaCarga");
        given().when().get(BASE + "/retrieve/" + id)
                .then().body("fechaActualizacion", equalTo(fechaCarga));

        validar(19.43, -99.13, id, true);   // interior
        validar(19.42, -99.13, id, true);   // sobre el borde
        validar(25.67, -100.31, id, false); // fuera

        given().contentType(ContentType.JSON).body(cuerpo("Zona Monterrey", POLIGONO_MTY))
                .when().put(BASE + "/update/" + id)
                .then().statusCode(200)
                .body("nombre", equalTo("Zona Monterrey"))
                .body("fechaCarga", equalTo(fechaCarga))
                .body("fechaActualizacion", org.hamcrest.Matchers.not(equalTo(fechaCarga)))
                .body("geometria.coordinates[0][0]", contains(-100.33f, 25.66f));

        validar(25.67, -100.31, id, true);
        validar(19.43, -99.13, id, false);

        given().when().delete(BASE + "/delete/" + id).then().statusCode(204);
        given().when().get(BASE + "/retrieve/" + id).then().statusCode(404);
        given().when().delete(BASE + "/delete/" + id).then().statusCode(404);
    }

    @Test
    void validarDevuelveTodasLasGeocercasQueContienenElPunto() {
        long grande = registrar("Grande", """
                {"type":"Polygon","coordinates":[[[10,10],[20,10],[20,20],[10,20],[10,10]]]}""");
        long chica = registrar("Chica", """
                {"type":"MultiPolygon","coordinates":[[[[14,14],[16,14],[16,16],[14,16],[14,14]]]]}""");

        given().contentType(ContentType.JSON).body("{\"latitud\":15,\"longitud\":15}")
                .when().post(BASE + "/validate")
                .then().statusCode(200)
                .body("dentro", equalTo(true))
                .body("geocercas.idGeocerca", contains((int) grande, (int) chica));

        given().contentType(ContentType.JSON).body("{\"latitud\":-45,\"longitud\":-45}")
                .when().post(BASE + "/validate")
                .then().statusCode(200)
                .body("dentro", equalTo(false))
                .body("geocercas", empty());
    }

    @Test
    void rechazaGeometriasInvalidas() {
        // Tipo no permitido
        given().contentType(ContentType.JSON)
                .body(cuerpo("Punto", "{\"type\":\"Point\",\"coordinates\":[-99.13,19.43]}"))
                .when().post(BASE + "/register")
                .then().statusCode(400)
                .body("mensaje", containsString("Polygon o MultiPolygon"));

        // Polígono auto-intersectado ("moño")
        given().contentType(ContentType.JSON)
                .body(cuerpo("Moño", "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,1],[1,0],[0,1],[0,0]]]}"))
                .when().post(BASE + "/register")
                .then().statusCode(400)
                .body("mensaje", containsString("no es válida"));

        // Coordenadas invertidas [latitud, longitud]: latitud -99 fuera de rango
        given().contentType(ContentType.JSON)
                .body(cuerpo("Invertida", """
                        {"type":"Polygon","coordinates":[[[19.28,-99.05],[19.27,-99.05],[19.27,-99.04],[19.28,-99.04],[19.28,-99.05]]]}"""))
                .when().post(BASE + "/register")
                .then().statusCode(400)
                .body("mensaje", containsString("[longitud, latitud]"));

        // GeoJSON no interpretable
        given().contentType(ContentType.JSON)
                .body(cuerpo("Basura", "{\"type\":\"Polygon\",\"coordinates\":\"x\"}"))
                .when().post(BASE + "/register")
                .then().statusCode(400)
                .body("mensaje", containsString("GeoJSON"));
    }

    @Test
    void rechazaSolicitudesIncompletas() {
        given().contentType(ContentType.JSON).body("{\"nombre\":\"\"}")
                .when().post(BASE + "/register")
                .then().statusCode(400)
                .body("detalles", hasItem(containsString("geometria")))
                .body("detalles", hasItem(containsString("nombre")));

        given().contentType(ContentType.JSON).body("{\"latitud\":95,\"longitud\":-99}")
                .when().post(BASE + "/validate")
                .then().statusCode(400)
                .body("detalles", hasItem(containsString("latitud")));

        given().contentType(ContentType.JSON).body(cuerpo("X", POLIGONO_CENTRO))
                .when().put(BASE + "/update/999999")
                .then().statusCode(404);
    }
}
