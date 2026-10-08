#!/usr/bin/env python3
"""
Genera mapa-geocercas.html: una página autónoma (Leaflet) con todas las geocercas de la base de datos
sobre los límites estatales de México.

Los datos se leen de PostGIS con tablas TEMP (no se modifica el esquema). El punto de cada geocerca es
el lugar del catálogo de carga (scripts/carga-geocercas) cuando existe; si no, ST_PointOnSurface.

Uso: python3 generar_mapa.py [--contenedor geofence-db]
"""
import argparse
import json
import subprocess
import sys
from pathlib import Path

AQUI = Path(__file__).parent
CARGA = AQUI.parent / "carga-geocercas"
NOMBRES_ESTADO = {"Distrito Federal": "Ciudad de México", "México": "Estado de México"}


def sql_texto(s):
    return "'" + s.replace("'", "''") + "'"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--contenedor", default="geofence-db")
    ap.add_argument("--usuario", default="geofence")
    ap.add_argument("--base", default="geofence")
    ap.add_argument("--salida", default=str(AQUI / "mapa-geocercas.html"))
    args = ap.parse_args()

    estados = [e for e in json.loads((CARGA / "mx_estados.geojson").read_text())["features"]
               if e["properties"].get("name")]
    lugares = json.loads((CARGA / "geocercas.json").read_text())

    sql = ["\\set ON_ERROR_STOP 1", "CREATE TEMP TABLE estados(nombre text, geom geometry);"]
    for e in estados:
        nombre = NOMBRES_ESTADO.get(e["properties"]["name"], e["properties"]["name"])
        sql.append(f"INSERT INTO estados VALUES ({sql_texto(nombre)}, "
                   f"ST_SetSRID(ST_GeomFromGeoJSON({sql_texto(json.dumps(e['geometry']))}), 4326));")
    sql.append("CREATE TEMP TABLE lugares(nombre text, lat float8, lon float8);")
    for l in lugares:
        sql.append(f"INSERT INTO lugares VALUES ({sql_texto(l['nombre'])}, {l['latitud']}, {l['longitud']});")
    sql.append("""
WITH g AS (
  SELECT c.*, COALESCE((SELECT ST_SetSRID(ST_MakePoint(l.lon, l.lat), 4326) FROM lugares l
                        WHERE l.nombre = c.nombre
                          AND ST_Covers(c.geometria, ST_SetSRID(ST_MakePoint(l.lon, l.lat), 4326)) LIMIT 1),
                       ST_PointOnSurface(c.geometria)) AS punto,
         EXISTS (SELECT 1 FROM lugares l WHERE l.nombre = c.nombre) AS de_catalogo
  FROM geocercas c
)
SELECT json_build_object(
  'generado', to_char(now() AT TIME ZONE 'America/Mexico_City', 'YYYY-MM-DD HH24:MI'),
  'estados', (SELECT json_agg(json_build_object('nombre', nombre,
                'geometria', ST_AsGeoJSON(ST_SimplifyPreserveTopology(geom, 0.01), 3)::json)) FROM estados),
  'geocercas', (SELECT json_agg(json_build_object(
                'id', g.id_geocerca, 'nombre', g.nombre,
                'estado', (SELECT e.nombre FROM estados e ORDER BY ST_Distance(e.geom, g.punto) LIMIT 1),
                'carga', to_char(g.fecha_carga AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                'actualizacion', to_char(g.fecha_actualizacion AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                'area_km2', round((ST_Area(g.geometria::geography) / 1e6)::numeric, 3),
                'vertices', ST_NPoints(g.geometria),
                'catalogo', g.de_catalogo,
                'punto', json_build_array(round(ST_Y(g.punto)::numeric, 6), round(ST_X(g.punto)::numeric, 6)),
                'geometria', ST_AsGeoJSON(g.geometria, 6)::json) ORDER BY g.id_geocerca) FROM g)
);""")

    res = subprocess.run(["docker", "exec", "-i", args.contenedor, "psql", "-U", args.usuario, "-d", args.base,
                          "-qtA"], input="\n".join(sql), capture_output=True, text=True)
    if res.returncode != 0:
        sys.exit(res.stderr)
    datos = res.stdout.strip()

    html = (AQUI / "plantilla.html").read_text()
    html = html.replace("/*__LEAFLET_CSS__*/", (AQUI / "leaflet.css").read_text())
    html = html.replace("/*__DATOS__*/null", datos)
    Path(args.salida).write_text(html)
    n = len(json.loads(datos)["geocercas"])
    print(f"{n} geocercas -> {args.salida} ({len(html) / 1024:.0f} KB)")


if __name__ == "__main__":
    main()
