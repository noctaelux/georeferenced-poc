# Geofence API — Quarkus + PostgreSQL/PostGIS

API REST para administrar un catálogo de geocercas georreferenciadas y validar si una coordenada
cae dentro de alguna de ellas.

| Componente | Versión |
|---|---|
| Quarkus | 3.40.1 (Java 21+) |
| PostgreSQL | 17 |
| PostGIS | 3.6 |

## Estructura

```
.
├── docker-compose.yml          # Despliegue: BD (PostGIS) + API
├── docker/postgis/             # Imagen de BD multi-arquitectura (amd64/arm64)
│   ├── Dockerfile
│   └── initdb/01-postgis.sql   # Habilita la extensión postgis al crear la BD
└── geofence-api/               # Aplicación Quarkus
    ├── Dockerfile              # Build multi-etapa (Maven -> JRE)
    └── src/main
        ├── java/com/poc/geofence
        │   ├── entity/         # Geocerca (tabla GEOCERCAS)
        │   ├── repository/     # Consultas espaciales (ST_Covers, ST_IsValid…)
        │   ├── service/        # Reglas de negocio y validación de geometrías
        │   ├── resource/       # Endpoints REST
        │   ├── dto/
        │   └── exception/
        └── resources/db/migration/V1__crear_tabla_geocercas.sql   # Flyway
```

## Tabla `geocercas`

| Columna | Tipo | Notas |
|---|---|---|
| `id_geocerca` | `BIGSERIAL` | PK |
| `nombre` | `VARCHAR(150)` | obligatorio |
| `fecha_carga` | `TIMESTAMPTZ` | se fija al registrar |
| `fecha_actualizacion` | `TIMESTAMPTZ` | se actualiza en cada modificación |
| `geometria` | `geometry(Geometry, 4326)` | solo `POLYGON`/`MULTIPOLYGON` válidos (CHECK); índice **GIST** |

## Despliegue con Docker

```bash
docker compose up -d --build
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/q/swagger-ui
- OpenAPI: http://localhost:8080/q/openapi
- Health: http://localhost:8080/q/health
- PostgreSQL/PostGIS: `localhost:5432`, base `geofence`, usuario/contraseña `geofence`/`geofence`

La tabla la crea Flyway al iniciar la API. Para levantar **solo la base de datos**:

```bash
docker compose up -d db
```

Variables de entorno de la API: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`.

## Desarrollo local

```bash
cd geofence-api
./mvnw quarkus:dev   # Dev Services levanta un PostGIS temporal automáticamente
./mvnw test          # Pruebas de integración contra PostGIS real (requiere Docker)
```

> En Dev Services se usa `postgis/postgis:17-3.5`, que solo existe para amd64; en Apple Silicon corre
> emulado (funciona, pero arranca más lento). El contenedor de `docker compose` es nativo arm64/amd64.

## Datos de ejemplo: 300 geocercas en México

`scripts/carga-geocercas/` contiene 300 geocercas de los 32 estados, cada una nombrada por un lugar
relevante (monumento, zona arqueológica, parque, pueblo mágico…) cuya coordenada queda dentro del polígono.

```bash
cd scripts/carga-geocercas
python3 cargar.py      # registra vía API y verifica cada punto con /validate (idempotente)
python3 generar.py     # opcional: regenera geocercas.json a partir de lugares.csv
```

- `lugares.csv`: catálogo de candidatos (nombre, estado ISO, latitud, longitud, radio en km).
- `generar.py`: crea un polígono irregular alrededor de cada punto, sin traslapes entre vecinos; con
  PostGIS y los límites estatales de Natural Earth (`mx_estados.geojson`) verifica que el punto esté
  en el estado declarado y recorta el polígono al territorio nacional. Descarta los que no cumplen.
- `geocercas.json`: resultado listo para cargar.

## Mapa de geocercas

`scripts/mapa/` genera `mapa-geocercas.html`, una página autónoma (Leaflet) con todas las geocercas de
la base sobre los límites estatales: buscador, filtro por estado y ficha de cada geocerca.

```bash
python3 scripts/mapa/generar_mapa.py   # vuelve a leer la base y regenera el HTML
open scripts/mapa/mapa-geocercas.html
```

Enlace directo a una geocerca: `mapa-geocercas.html#g<id>` (p. ej. `#g62`).

## Prueba de carga de `/validate`

`scripts/estres/validate.js` (k6): tasa constante de `TPS` peticiones/s durante `DURACION` (por defecto
10 TPS durante 30 s); 70 % puntos del catálogo
(verifica que la respuesta incluya esa geocerca) y 30 % puntos aleatorios en México. Falla si hay
errores, p95 ≥ 200 ms, p99 ≥ 500 ms o si no se sostiene la tasa.

```bash
docker run --rm --network georeferenced-poc_default -e TPS=60 -e DURACION=30s \
  -v "$PWD/scripts/estres:/scripts:ro" -v "$PWD/scripts/carga-geocercas:/datos:ro" \
  grafana/k6:latest run /scripts/validate.js
```

Modo rampa (sin umbrales; cada etapa sube en 10 s y se mantiene 20 s) y análisis por etapa:

```bash
docker run --rm --name k6-rampa --network georeferenced-poc_default -e ETAPAS=100,200,400,800,1600 \
  -v "$PWD/scripts/estres:/scripts:ro" -v "$PWD/scripts/carga-geocercas:/datos:ro" \
  -v "$PWD/scripts/estres/resultados/rampa:/resultados" \
  grafana/k6:latest run --out csv=/resultados/series.csv /scripts/validate.js
python3 scripts/estres/analizar_rampa.py scripts/estres/resultados/rampa 100,200,400,800,1600
```

`analizar_rampa.py` lee además `docker_stats.csv` (CPU por contenedor) del mismo directorio si existe.

## Endpoints

Base: `/api/v1/geofence`. Geometrías en **GeoJSON**, WGS84 (EPSG:4326), coordenadas en orden
`[longitud, latitud]`.

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `GET` | `/retrieve/{id}` | Recupera una geocerca | 200, 404 |
| `POST` | `/register` | Registra una geocerca | 201, 400 |
| `PUT` | `/update/{id}` | Actualiza nombre y geometría | 200, 400, 404 |
| `POST` | `/validate` | ¿La coordenada está dentro de alguna geocerca? | 200, 400 |
| `DELETE` | `/delete/{id}` | Elimina la geocerca del catálogo | 204, 404 |

### Ejemplos

```bash
B=http://localhost:8080/api/v1/geofence

# Registrar
curl -X POST $B/register -H 'Content-Type: application/json' -d '{
  "nombre": "Zona Centro",
  "geometria": {"type":"Polygon","coordinates":[[[-99.14,19.42],[-99.12,19.42],[-99.12,19.44],[-99.14,19.44],[-99.14,19.42]]]}
}'
# -> 201 {"idGeocerca":1,"nombre":"Zona Centro","fechaCarga":"…","fechaActualizacion":"…","geometria":{…}}

# Recuperar
curl $B/retrieve/1

# Validar coordenada
curl -X POST $B/validate -H 'Content-Type: application/json' -d '{"latitud":19.43,"longitud":-99.13}'
# -> {"latitud":19.43,"longitud":-99.13,"dentro":true,"geocercas":[{"idGeocerca":1,"nombre":"Zona Centro"}]}

# Actualizar
curl -X PUT $B/update/1 -H 'Content-Type: application/json' -d '{
  "nombre": "Zona Centro ampliada",
  "geometria": {"type":"Polygon","coordinates":[[[-99.15,19.41],[-99.11,19.41],[-99.11,19.45],[-99.15,19.45],[-99.15,19.41]]]}
}'

# Eliminar
curl -X DELETE $B/delete/1
```

Errores con formato uniforme:

```json
{"status":400,"mensaje":"La geometría no es válida: Self-intersection[0.5 0.5]","detalles":[]}
```

## Decisiones de diseño

- **Validación espacial con `ST_Covers`**: un punto sobre el borde de la geocerca cuenta como
  "dentro". `validate` devuelve *todas* las geocercas que contienen el punto (pueden traslaparse). La
  consulta usa el índice GIST.
- **Geometrías validadas por PostGIS** antes de guardar: GeoJSON interpretable, tipo
  `Polygon`/`MultiPolygon`, SRID 4326, coordenadas dentro de rango (longitud ±180, latitud ±90; detecta
  el orden invertido `[latitud, longitud]`) y topología válida (`ST_IsValid`, con el motivo en el error).
  Las restricciones `CHECK` en la tabla garantizan lo mismo a nivel de base de datos.
- **Sin Hibernate Spatial/JTS**: la entidad mapea la geometría como GeoJSON con `@ColumnTransformer`
  (`ST_GeomFromGeoJSON` / `ST_AsGeoJSON`); PostGIS hace la conversión.
- **Esquema versionado con Flyway**; Hibernate no genera DDL.
