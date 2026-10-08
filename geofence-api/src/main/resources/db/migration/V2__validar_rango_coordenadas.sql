-- Rechaza geometrías con coordenadas fuera del rango WGS84 (p. ej. [latitud, longitud] invertidas)
ALTER TABLE geocercas
    ADD CONSTRAINT ck_geocercas_coordenadas_rango
        CHECK (ST_XMin(geometria) >= -180 AND ST_XMax(geometria) <= 180
           AND ST_YMin(geometria) >= -90  AND ST_YMax(geometria) <= 90);
