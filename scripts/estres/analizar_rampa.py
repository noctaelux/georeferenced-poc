#!/usr/bin/env python3
"""
Analiza una corrida de rampa de validate.js (modo ETAPAS) etapa por etapa, usando solo la ventana
estable de cada etapa (después de la SUBIDA).

Uso: python3 analizar_rampa.py <dir_resultados> <etapas, ej. 100,200,400> [subida_s=10] [mantener_s=20]

Degradación: p95 > 200 ms, errores > 0.1 % o tasa lograda < 95 % de la objetivo.
"""
import collections
import csv
import sys


def pct(v, p):
    return v[min(len(v) - 1, int(p * len(v)))] if v else float("nan")


def main():
    d, etapas = sys.argv[1], [int(x) for x in sys.argv[2].split(",")]
    subida = int(sys.argv[3]) if len(sys.argv) > 3 else 10
    mantener = int(sys.argv[4]) if len(sys.argv) > 4 else 20

    dur = collections.defaultdict(list)
    fallos, chk_mal, omitidas = collections.Counter(), collections.Counter(), collections.Counter()
    t0 = None
    for r in csv.DictReader(open(f"{d}/series.csv")):
        t = int(r["timestamp"])
        t0 = t if t0 is None else min(t0, t)
        m, v = r["metric_name"], float(r["metric_value"])
        if m == "http_req_duration":
            dur[t].append(v)
        elif m == "http_req_failed":
            fallos[t] += v
        elif m == "checks" and v == 0:
            chk_mal[t] += 1
        elif m == "dropped_iterations":
            omitidas[t] += v

    cpu = collections.defaultdict(lambda: collections.defaultdict(list))
    try:
        for ts, nombre, c, _ in csv.reader(open(f"{d}/docker_stats.csv")):
            cpu[nombre][int(ts)].append(float(c.rstrip("%")))
    except FileNotFoundError:
        pass

    def cpu_rango(nombre, a, b):
        v = [x for k, l in cpu[nombre].items() if a <= k - t0 < b for x in l]
        return f"{sum(v) / len(v):4.0f}/{max(v):4.0f}%" if v else "     -    "

    print(f"{'TPS':>6} {'logrado':>8} {'%':>6} {'p50':>6} {'p95':>6} {'p99':>7} {'máx':>7} {'err':>4} "
          f"{'chk✗':>4} {'omit':>5} | {'CPU api':>10} {'CPU db':>10} {'CPU k6':>10} | estado")
    print(f"{'':>6} {'(req/s)':>8} {'':>6} {'(ms)':>6} {'(ms)':>6} {'(ms)':>7} {'(ms)':>7} {'':>4} {'':>4} "
          f"{'':>5} | {'prom/máx':>10} {'prom/máx':>10} {'prom/máx':>10} |")
    ciclo = subida + mantener
    for i, tps in enumerate(etapas):
        a, b = t0 + ciclo * i + subida, t0 + ciclo * (i + 1)
        v = sorted(x for s in range(a, b) for x in dur.get(s, []))
        logrado = len(v) / mantener
        err = sum(fallos.get(s, 0) for s in range(a, b))
        omit = int(sum(omitidas.get(s, 0) for s in range(a, b)))
        degradada = pct(v, .95) > 200 or err > 0.001 * len(v) or logrado < 0.95 * tps
        print(f"{tps:>6} {logrado:>8.0f} {100 * logrado / tps:>5.1f}% {pct(v, .5):>6.2f} {pct(v, .95):>6.2f} "
              f"{pct(v, .99):>7.2f} {v[-1]:>7.1f} {int(err):>4} {sum(chk_mal.get(s, 0) for s in range(a, b)):>4} "
              f"{omit:>5} | {cpu_rango('geofence-api', a - t0, b - t0)} {cpu_rango('geofence-db', a - t0, b - t0)} "
              f"{cpu_rango('k6-rampa', a - t0, b - t0)} | {'DEGRADADA' if degradada else 'ok'}")

    picos = [(s - t0, round(max(l), 1)) for s, l in sorted(dur.items()) if max(l) > 100]
    print("\nSegundos con alguna petición > 100 ms (t, máx ms):", picos or "ninguno")
    print("Peticiones omitidas por segundo (t, n):",
          [(s - t0, int(n)) for s, n in sorted(omitidas.items()) if n] or "ninguna")
    print("CPU: 100 % = 1 núcleo (Docker dispone de 8).")


if __name__ == "__main__":
    main()
