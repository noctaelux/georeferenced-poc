CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE geocercas (
    id_geocerca         BIGSERIAL PRIMARY KEY,
    nombre              VARCHAR(150)             NOT NULL,
    fecha_carga         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    fecha_actualizacion TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    geometria           geometry(Geometry, 4326) NOT NULL,
    CONSTRAINT ck_geocercas_tipo_geometria
        CHECK (GeometryType(geometria) IN ('POLYGON', 'MULTIPOLYGON')),
    CONSTRAINT ck_geocercas_geometria_valida
        CHECK (ST_IsValid(geometria))
);

-- Índice espacial para acelerar las consultas de contención (validate)
CREATE INDEX idx_geocercas_geometria ON geocercas USING GIST (geometria);
