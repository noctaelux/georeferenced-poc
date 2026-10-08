#!/usr/bin/env python3
"""
Registra las geocercas de geocercas.json mediante la API y verifica cada una con /validate.

Es idempotente: si el punto de un lugar ya cae dentro de una geocerca con el mismo nombre,
no se vuelve a registrar.

Uso: python3 cargar.py [--api http://localhost:8080/api/v1/geofence]
"""
import argparse
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

AQUI = Path(__file__).parent


def llamar(url, cuerpo):
    req = urllib.request.Request(url, data=json.dumps(cuerpo).encode(), method="POST",
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.load(r)
    except urllib.error.HTTPError as e:
        return e.code, json.load(e)


def validar(api, lugar):
    _, r = llamar(f"{api}/validate", {"latitud": lugar["latitud"], "longitud": lugar["longitud"]})
    return r["geocercas"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--api", default="http://localhost:8080/api/v1/geofence")
    args = ap.parse_args()

    lugares = json.loads((AQUI / "geocercas.json").read_text())
    registrados, omitidos, fallidos = {}, 0, []

    for lugar in lugares:
        if any(g["nombre"] == lugar["nombre"] for g in validar(args.api, lugar)):
            omitidos += 1
            continue
        status, r = llamar(f"{args.api}/register", {"nombre": lugar["nombre"], "geometria": lugar["geometria"]})
        if status == 201:
            registrados[lugar["nombre"]] = r["idGeocerca"]
        else:
            fallidos.append(f"{lugar['nombre']}: HTTP {status} {r.get('mensaje')}")

    print(f"Registrados: {len(registrados)} | ya existían: {omitidos} | fallidos: {len(fallidos)}")
    for f in fallidos:
        print("  - " + f)

    # Verificación: el punto de cada lugar debe quedar dentro de su geocerca
    sin_cubrir = [l["nombre"] for l in lugares
                  if not any(g["nombre"] == l["nombre"] for g in validar(args.api, l))]
    print(f"Verificación /validate: {len(lugares) - len(sin_cubrir)}/{len(lugares)} puntos dentro de su geocerca")
    for n in sin_cubrir:
        print("  - sin cubrir: " + n)

    if registrados:
        ids = sorted(registrados.values())
        print(f"Ids asignados: {ids[0]}–{ids[-1]}")
    sys.exit(1 if fallidos or sin_cubrir else 0)


if __name__ == "__main__":
    main()
