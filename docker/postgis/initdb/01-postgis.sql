-- Se ejecuta solo la primera vez que se inicializa el volumen de datos.
-- La tabla GEOCERCAS la crea la aplicación mediante Flyway (db/migration).
CREATE EXTENSION IF NOT EXISTS postgis;
