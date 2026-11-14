"""
Carga y harmonizacion de detecciones FIRMS en PostgreSQL.

Uso:
  python etl/load_pg.py --truncate
  python etl/load_pg.py --only SV_ARCHIVE
  python etl/load_pg.py --aggregates

Requiere: duckdb (extension postgres), PostgreSQL con la base 'chronos'.
"""
import argparse
import os
import time

import duckdb

PG_DSN = os.environ.get(
    "CHRONOS_PG",
    "host=127.0.0.1 port=5432 dbname=chronos_fire user=postgres",
)

DATA = r"C:\Users\HP\Desktop\NASA Space Apps Challenge - 2026\Hackaton-NASA-Chronos_Fire\data"
TMP = r"C:\Users\HP\AppData\Local\Temp\opencode\firms"

# (clave, ruta, lector, sensor, source, tiene_type)
SOURCES = [
    ("SV_ARCHIVE", os.path.join(DATA, r"DL_FIRE_SV-C2_848443\fire_archive_SV-C2_848443.json"),
     "json", "VIIRS_SNPP", "SP", True),
    ("SV_NRT", os.path.join(DATA, r"DL_FIRE_SV-C2_848443\fire_nrt_SV-C2_848443.json"),
     "json", "VIIRS_SNPP", "NRT", False),
    # NOAA-20: el JSON descargado solo cubre 2026-04..07; usamos el CSV completo 2018-2026.
    ("J1V_ARCHIVE", os.path.join(TMP, "fire_archive_J1V-C2_848256.csv"),
     "csv", "VIIRS_NOAA20", "SP", True),
    ("J1V_NRT", os.path.join(TMP, "fire_nrt_J1V-C2_848256.csv"),
     "csv", "VIIRS_NOAA20", "NRT", False),
    ("J2V_ARCHIVE", os.path.join(DATA, r"DL_FIRE_J2V-C2_848419\fire_archive_J2V-C2_848419.json"),
     "json", "VIIRS_NOAA21", "SP", True),
    ("J2V_NRT", os.path.join(DATA, r"DL_FIRE_J2V-C2_848419\fire_nrt_J2V-C2_848419.json"),
     "json", "VIIRS_NOAA21", "NRT", False),
    ("M_ARCHIVE", os.path.join(DATA, r"DL_FIRE_M-C61_848444\fire_archive_M-C61_848444.json"),
     "json", "MODIS", "SP", True),
    ("M_NRT", os.path.join(TMP, "fire_nrt_M-C61_848249.csv"),
     "csv", "MODIS", "NRT", False),
]

COLUMNS = """sensor, source, product_version, satellite, instrument, acquired_at, acq_date,
acq_time_utc, latitude, longitude, brightness, bright_t31, scan_km, track_km,
pixel_area_km2, frp_mw, frp_density_wm2, confidence_raw, confidence_norm,
confidence_class, daynight, fire_type, is_vegetation, quality_flags, harmonization_ver"""


def build_select(path, reader, sensor, source, has_type):
    path = path.replace("\\", "/")
    if reader == "json":
        from_clause = f"read_json_auto('{path}')"
    else:
        from_clause = f"read_csv_auto('{path}', header=true)"

    type_expr = "TRY_CAST(type AS SMALLINT)" if has_type else "NULL::SMALLINT"

    if sensor == "MODIS":
        conf_norm = "CAST(CAST(confidence AS INT) AS SMALLINT)"
        conf_class = ("CASE WHEN CAST(confidence AS INT) >= 80 THEN 'high' "
                      "WHEN CAST(confidence AS INT) >= 50 THEN 'nominal' ELSE 'low' END")
    else:
        conf_norm = "CASE confidence WHEN 'h' THEN 90 WHEN 'n' THEN 50 WHEN 'l' THEN 20 END"
        conf_class = ("CASE confidence WHEN 'h' THEN 'high' WHEN 'n' THEN 'nominal' "
                      "WHEN 'l' THEN 'low' END")

    version_expr = ("CASE WHEN TRY_CAST(version AS DOUBLE) = 2 THEN '2' "
                    "ELSE CAST(version AS VARCHAR) END")

    return f"""
    SELECT
      '{sensor}' AS sensor,
      '{source}' AS source,
      {version_expr} AS product_version,
      CAST(satellite AS VARCHAR) AS satellite,
      CAST(instrument AS VARCHAR) AS instrument,
      CAST(strptime(CAST(acq_date AS VARCHAR) || lpad(CAST(acq_time AS VARCHAR), 4, '0'),
                    '%Y-%m-%d%H%M') AS TIMESTAMPTZ) AS acquired_at,
      CAST(acq_date AS DATE) AS acq_date,
      CAST(lpad(CAST(acq_time AS VARCHAR), 4, '0') AS SMALLINT) AS acq_time_utc,
      CAST(latitude AS DOUBLE) AS latitude,
      CAST(longitude AS DOUBLE) AS longitude,
      CAST(brightness AS REAL) AS brightness,
      CAST(bright_t31 AS REAL) AS bright_t31,
      CAST(scan AS REAL) AS scan_km,
      CAST(track AS REAL) AS track_km,
      CAST(scan AS REAL) * CAST(track AS REAL) AS pixel_area_km2,
      CAST(frp AS REAL) AS frp_mw,
      CAST(frp AS REAL) / NULLIF(CAST(scan AS REAL) * CAST(track AS REAL), 0) AS frp_density_wm2,
      CAST(confidence AS VARCHAR) AS confidence_raw,
      CAST({conf_norm} AS SMALLINT) AS confidence_norm,
      {conf_class} AS confidence_class,
      CAST(daynight AS CHAR(1)) AS daynight,
      {type_expr} AS fire_type,
      CASE WHEN {type_expr} = 0 THEN TRUE
           WHEN {type_expr} IS NULL THEN NULL
           ELSE FALSE END AS is_vegetation,
      NULLIF(concat_ws(',',
        CASE WHEN CAST(frp AS REAL) = 0 THEN 'frp_zero' END,
        CASE WHEN CAST(brightness AS REAL) <= 250 THEN 'low_brightness' END,
        CASE WHEN {type_expr} IN (2,3) THEN 'industrial_or_offshore' END,
        CASE WHEN CAST(scan AS REAL) * CAST(track AS REAL) = 0 THEN 'bad_geometry' END
      ), '') AS quality_flags,
      'v1' AS harmonization_ver
    FROM {from_clause}
    """


def load(con, key, path, reader, sensor, source, has_type):
    t0 = time.time()
    select_sql = build_select(path, reader, sensor, source, has_type)
    # Deduplicacion por pixel + instante: conserva la deteccion de mayor FRP.
    con.execute(
        "CREATE OR REPLACE TEMP TABLE stg AS "
        "WITH base AS (" + select_sql + ") "
        "SELECT * EXCLUDE (rn) FROM ("
        "  SELECT *, row_number() OVER ("
        "    PARTITION BY latitude, longitude, acquired_at "
        "    ORDER BY frp_mw DESC NULLS LAST, brightness DESC NULLS LAST"
        "  ) AS rn FROM base"
        ") WHERE rn = 1"
    )
    n = con.execute("SELECT count(*) FROM stg").fetchone()[0]
    t1 = time.time()
    con.execute(f"INSERT INTO pg.detections ({COLUMNS}) SELECT * FROM stg")
    t2 = time.time()
    print(f"  {key}: {n:,} filas | stage {t1-t0:.1f}s | insert {t2-t1:.1f}s")
    return n


def aggregates(con):
    t0 = time.time()
    print("Construyendo grid_daily (0.05 grados)...")
    con.execute("TRUNCATE pg.grid_daily")
    con.execute("""
    INSERT INTO pg.grid_daily
    WITH base AS (
      SELECT
        '0.05:' || CAST(round(latitude / 0.05) AS BIGINT) || ':' ||
                  CAST(round(longitude / 0.05) AS BIGINT) AS grid_id,
        round(latitude / 0.05) * 0.05  AS cell_lat,
        round(longitude / 0.05) * 0.05 AS cell_lon,
        acq_date AS day,
        sensor,
        frp_mw,
        confidence_norm,
        (is_vegetation IS TRUE OR is_vegetation IS NULL) AS veg_ok,
        confidence_class IN ('nominal','high') AS conf_ok
      FROM pg.detections
    )
    SELECT
      grid_id, cell_lat, cell_lon, day,
      count(*)::int AS n_detections,
      bool_or(veg_ok AND conf_ok) AS fire_day,
      sum(frp_mw) FILTER (WHERE veg_ok AND conf_ok)::real AS frp_sum_mw,
      max(frp_mw) FILTER (WHERE veg_ok AND conf_ok)::real AS frp_max_mw,
      array_agg(DISTINCT sensor) AS sensors,
      max(confidence_norm)::smallint AS confidence_max
    FROM base
    GROUP BY 1,2,3,4
    """)
    print(f"  grid_daily: {con.execute('SELECT count(*) FROM pg.grid_daily').fetchone()[0]:,} filas ({time.time()-t0:.1f}s)")

    t0 = time.time()
    print("Construyendo calendar_weekly (climatologia)...")
    con.execute("TRUNCATE pg.calendar_weekly")
    con.execute("""
    INSERT INTO pg.calendar_weekly
    SELECT
      grid_id,
      min(cell_lat)::double precision AS cell_lat,
      min(cell_lon)::double precision AS cell_lon,
      CAST(extract(week FROM day) AS SMALLINT) AS week_of_year,
      count(DISTINCT extract(year FROM day))::int AS n_years,
      avg(fire_day::int)::real AS fire_day_prob,
      percentile_cont(0.5) WITHIN GROUP (ORDER BY frp_sum_mw)::real AS frp_p50,
      percentile_cont(0.9) WITHIN GROUP (ORDER BY frp_sum_mw)::real AS frp_p90
    FROM pg.grid_daily
    GROUP BY 1, 4
    """)
    print(f"  calendar_weekly: {con.execute('SELECT count(*) FROM pg.calendar_weekly').fetchone()[0]:,} filas ({time.time()-t0:.1f}s)")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--truncate", action="store_true")
    ap.add_argument("--only", nargs="*")
    ap.add_argument("--aggregates", action="store_true")
    args = ap.parse_args()

    con = duckdb.connect()
    con.execute("INSTALL postgres; LOAD postgres;")
    con.execute(f"ATTACH '{PG_DSN}' AS pg (TYPE postgres);")
    con.execute("SET TimeZone='UTC';")
    con.execute("SET preserve_insertion_order=false;")
    con.execute(f"SET temp_directory='{os.path.join(TMP, 'duck_tmp').replace(chr(92), '/')}';")

    if args.truncate:
        print("TRUNCATE detections")
        con.execute("TRUNCATE pg.detections")

    total = 0
    for key, path, reader, sensor, source, has_type in SOURCES:
        if args.only and key not in args.only:
            continue
        if not os.path.exists(path):
            print(f"  {key}: FALTA {path}")
            continue
        print(f"Cargando {key} ({sensor} {source})...")
        total += load(con, key, path, reader, sensor, source, has_type)

    print("Total cargado en esta corrida:", f"{total:,}")
    print("En detections:", f"{con.execute('SELECT count(*) FROM pg.detections').fetchone()[0]:,}")
    print(con.execute("""
        SELECT sensor, source, count(*) AS n, min(acq_date) AS desde, max(acq_date) AS hasta
        FROM pg.detections GROUP BY 1,2 ORDER BY 1,2
    """).fetchdf().to_string(index=False))

    if args.aggregates:
        aggregates(con)


if __name__ == "__main__":
    main()

