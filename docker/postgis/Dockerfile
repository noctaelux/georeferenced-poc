# PostgreSQL 17 + PostGIS 3 (multi-arquitectura: amd64 y arm64).
# La imagen oficial postgis/postgis solo se publica para amd64; esta se construye sobre
# postgres:17, que ya trae configurado el repositorio PGDG.
FROM postgres:17

ENV POSTGIS_MAJOR=3

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        postgresql-$PG_MAJOR-postgis-$POSTGIS_MAJOR \
        postgresql-$PG_MAJOR-postgis-$POSTGIS_MAJOR-scripts \
    && rm -rf /var/lib/apt/lists/*

COPY initdb/ /docker-entrypoint-initdb.d/
