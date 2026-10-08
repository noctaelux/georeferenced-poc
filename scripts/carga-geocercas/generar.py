#!/usr/bin/env python3
"""
Genera geocercas.json a partir de lugares.csv.

Por cada lugar se crea un polígono irregular alrededor de su coordenada. Después, en PostGIS
(usando tablas TEMP que se descartan al cerrar la sesión):
  - se verifica que el punto caiga dentro del estado declarado (límites de Natural Earth),
  - se recorta el polígono al territorio nacional,
  - se verifica que el punto siga cubierto por el polígono recortado y que la geometría sea válida.

Uso: python3 generar.py [--contenedor geofence-db] [--limite 300]
"""
import argparse
import csv
import json
import math
import random
import subprocess
import sys
import urllib.request
from pathlib import Path

AQUI = Path(__file__).parent
NE_URL = ("https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/"
          "geojson/ne_10m_admin_1_states_provinces.geojson")
ESTADOS = AQUI / "mx_estados.geojson"
KM_POR_GRADO = 111.32
FACTOR_MAX = 1.2          # radio máximo de un vértice respecto al radio nominal
SEPARACION = 0.45         # fracción de la distancia al vecino más cercano que puede ocupar cada polígono


def descargar_estados():
    if ESTADOS.exists():
        return
    print("Descargando límites estatales de Natural Earth…", file=sys.stderr)
    with urllib.request.urlopen(NE_URL) as r:
        datos = json.load(r)
    mx = [f for f in datos["features"] if f["properties"].get("adm0_a3") == "MEX"]
    ESTADOS.write_text(json.dumps({"type": "FeatureCollection", "features": mx}))


def distancia_km(a, b):
    lat1, lon1, lat2, lon2 = map(math.radians, (a["lat"], a["lon"], b["lat"], b["lon"]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * 6371 * math.asin(math.sqrt(h))


def poligono(lugar, radio_km):
    """Polígono irregular (estrellado respecto al punto, por lo que siempre lo contiene)."""
    rnd = random.Random(lugar["nombre"])
    n = rnd.randint(6, 10)
    paso = 2 * math.pi / n
    inicio = rnd.uniform(0, paso)
    coords = []
    for i in range(n):
        ang = inicio + i * paso + rnd.uniform(-0.3, 0.3) * paso
        r = radio_km * rnd.uniform(0.7, FACTOR_MAX)
        dlat = r * math.sin(ang) / KM_POR_GRADO
        dlon = r * math.cos(ang) / (KM_POR_GRADO * math.cos(math.radians(lugar["lat"])))
        coords.append([round(lugar["lon"] + dlon, 6), round(lugar["lat"] + dlat, 6)])
    coords.append(coords[0])
    return {"type": "Polygon", "coordinates": [coords]}


def sql_texto(s):
    return "'" + s.replace("'", "''") + "'"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--contenedor", default="geofence-db")
    ap.add_argument("--usuario", default="geofence")
    ap.add_argument("--base", default="geofence")
    ap.add_argument("--limite", type=int, default=300)
    args = ap.parse_args()

    descargar_estados()
    with open(AQUI / "lugares.csv", encoding="utf-8") as f:
        lugares = [{"nombre": r["nombre"], "estado": r["estado"], "lat": float(r["latitud"]),
                    "lon": float(r["longitud"]), "radio": float(r["radio_km"])} for r in csv.DictReader(f)]

    nombres = [l["nombre"] for l in lugares]
    duplicados = {n for n in nombres if nombres.count(n) > 1}
    if duplicados:
        sys.exit(f"Nombres duplicados: {duplicados}")

    # Radio efectivo: evita que polígonos vecinos se traslapen
    for l in lugares:
        vecino = min(distancia_km(l, o) for o in lugares if o is not l)
        l["radio_efectivo"] = min(l["radio"], SEPARACION * vecino / FACTOR_MAX)
        l["geojson"] = json.dumps(poligono(l, l["radio_efectivo"]))

    estados = json.loads(ESTADOS.read_text())["features"]
    sql = ["\\set ON_ERROR_STOP 1",
           "CREATE TEMP TABLE estados(iso text, geom geometry);"]
    for e in estados:
        sql.append(f"INSERT INTO estados VALUES ({sql_texto(e['properties']['iso_3166_2'])}, "
                   f"ST_SetSRID(ST_GeomFromGeoJSON({sql_texto(json.dumps(e['geometry']))}), 4326));")
    sql.append("CREATE TEMP TABLE lugares(orden int, nombre text, iso text, lat float8, lon float8, poli text);")
    for i, l in enumerate(lugares):
        sql.append(f"INSERT INTO lugares VALUES ({i}, {sql_texto(l['nombre'])}, {sql_texto(l['estado'])}, "
                   f"{l['lat']}, {l['lon']}, {sql_texto(l['geojson'])});")
    sql.append("""
CREATE TEMP TABLE mexico AS SELECT ST_Union(geom) AS geom FROM estados;
WITH base AS (
  SELECT l.*, ST_SetSRID(ST_MakePoint(l.lon, l.lat), 4326) AS punto,
         ST_SetSRID(ST_GeomFromGeoJSON(l.poli), 4326) AS bruto
  FROM lugares l
), recorte AS (
  SELECT b.*, ST_CollectionExtract(ST_MakeValid(ST_Intersection(b.bruto, m.geom)), 3) AS g,
         (SELECT e.iso FROM estados e WHERE ST_Covers(e.geom, b.punto) LIMIT 1) AS iso_real
  FROM base b, mexico m
), salida AS (
  SELECT r.*, ST_GeomFromGeoJSON(ST_AsGeoJSON(r.g, 6)) AS g6 FROM recorte r
)
SELECT json_agg(json_build_object(
         'nombre', nombre, 'estado', iso, 'estado_real', iso_real, 'latitud', lat, 'longitud', lon,
         'recortado', NOT ST_Equals(ST_SnapToGrid(bruto, 1e-6), ST_SnapToGrid(g, 1e-6)),
         'cubre_punto', ST_Covers(ST_SetSRID(g6, 4326), punto),
         'valida', ST_IsValid(g6) AND NOT ST_IsEmpty(g6),
         'area_km2', round((ST_Area(g::geography) / 1e6)::numeric, 3),
         'geometria', ST_AsGeoJSON(g, 6)::json) ORDER BY orden)
FROM salida;""")

    res = subprocess.run(["docker", "exec", "-i", args.contenedor, "psql", "-U", args.usuario, "-d", args.base,
                          "-qtA"], input="\n".join(sql), capture_output=True, text=True)
    if res.returncode != 0:
        sys.exit(res.stderr)
    filas = json.loads(res.stdout.strip())

    errores = []
    for f in filas:
        if f["estado_real"] is None:
            errores.append(f"{f['nombre']}: el punto queda fuera de México")
        elif f["estado_real"] != f["estado"]:
            errores.append(f"{f['nombre']}: el punto cae en {f['estado_real']}, no en {f['estado']}")
        if not f["cubre_punto"]:
            errores.append(f"{f['nombre']}: el polígono recortado no cubre el punto")
        if not f["valida"]:
            errores.append(f"{f['nombre']}: geometría inválida o vacía tras el recorte")

    malos = {e.split(":")[0] for e in errores}
    buenos = [f for f in filas if f["nombre"] not in malos]
    print(f"Candidatos: {len(filas)} | con observaciones: {len(malos)} | aptos: {len(buenos)}", file=sys.stderr)
    for e in errores:
        print("  - " + e, file=sys.stderr)
    if len(buenos) < args.limite:
        sys.exit(f"Solo hay {len(buenos)} lugares aptos; se requieren {args.limite}")

    # Selección equilibrada: mientras sobren, se descarta el último lugar del estado con más lugares
    seleccion = list(buenos)
    while len(seleccion) > args.limite:
        por_estado = {}
        for f in seleccion:
            por_estado.setdefault(f["estado"], []).append(f)
        mayor = max(por_estado.values(), key=len)
        seleccion.remove(mayor[-1])
    recortados = sum(f["recortado"] for f in seleccion)
    radios = {l["nombre"]: l for l in lugares}
    reducidos = sum(radios[f["nombre"]]["radio_efectivo"] < radios[f["nombre"]]["radio"] for f in seleccion)
    areas = sorted(f["area_km2"] for f in seleccion)
    print(f"Seleccionados: {len(seleccion)} | estados: {len({f['estado'] for f in seleccion})} | "
          f"recortados a la frontera/costa: {recortados} | radio reducido por vecinos: {reducidos} | "
          f"área km² min/mediana/max: {areas[0]}/{areas[len(areas) // 2]}/{areas[-1]}", file=sys.stderr)

    salida = [{"nombre": f["nombre"], "estado": f["estado"], "latitud": f["latitud"],
               "longitud": f["longitud"], "geometria": f["geometria"]} for f in seleccion]
    (AQUI / "geocercas.json").write_text(json.dumps(salida, ensure_ascii=False, indent=1))
    print(f"Escrito {AQUI / 'geocercas.json'}", file=sys.stderr)


if __name__ == "__main__":
    main()
