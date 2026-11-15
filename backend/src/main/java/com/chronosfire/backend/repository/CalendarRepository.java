package com.chronosfire.backend.repository;

import com.chronosfire.backend.dto.Bbox;
import com.chronosfire.backend.dto.DailyRow;
import com.chronosfire.backend.dto.HeatmapCell;
import com.chronosfire.backend.service.RiskMath;
import java.sql.Array;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CalendarRepository {

    /**
     * Misma definicion que {@code fire_day} en {@code fix_calendar.sql}:
     * vegetacion efectiva, confianza nominal/alta y fuera de la mascara
     * industrial. Se usa para contar hotspots armonizados, no detecciones
     * crudas.
     */
    private static final String HOTSPOT_PREDICATE = """
            NOT industrial_mask
              AND confidence_class IN ('nominal', 'high')
              AND coalesce(is_vegetation_effective,
                           confidence_class IN ('nominal', 'high'))
            """;

    private final JdbcTemplate jdbc;

    public CalendarRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SensorWeek> bySensor(int year, int week, Bbox bbox) {
        return jdbc.query("""
                SELECT sensor,
                       coalesce(sum(fire_cells), 0)::int    AS fire_cells,
                       coalesce(sum(fire_days), 0)::int     AS fire_days,
                       coalesce(sum(n_detections), 0)::int  AS n_detections,
                       sum(frp_sum_mw)::float8             AS frp_sum
                FROM grid_weekly_sensor
                WHERE isoyear = ?
                  AND week_of_year = ?
                  AND cell_lat BETWEEN ? AND ?
                  AND cell_lon BETWEEN ? AND ?
                GROUP BY sensor
                ORDER BY sensor
                """, (rs, rowNum) -> new SensorWeek(
                rs.getString("sensor"),
                rs.getInt("fire_cells"),
                rs.getInt("fire_days"),
                rs.getInt("n_detections"),
                (Double) rs.getObject("frp_sum")),
                year, week,
                bbox.minLat(), bbox.maxLat(),
                bbox.minLon(), bbox.maxLon());
    }

    public Baseline baseline(
            String sensor,
            int week,
            int refFrom,
            int refTo,
            int excludeYear,
            Bbox bbox) {
        return jdbc.query("""
                WITH yearly AS (
                    SELECT isoyear, sum(fire_cells)::numeric AS n
                    FROM grid_weekly_sensor
                    WHERE sensor = ?
                      AND week_of_year = ?
                      AND isoyear BETWEEN ? AND ?
                      AND isoyear <> ?
                      AND cell_lat BETWEEN ? AND ?
                      AND cell_lon BETWEEN ? AND ?
                    GROUP BY isoyear
                )
                SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY n) AS median,
                       count(*)::int AS n_ref
                FROM yearly
                """, rs -> {
            if (!rs.next()) {
                return new Baseline(null, 0);
            }
            double median = rs.getDouble("median");
            Double value = rs.wasNull() ? null : median;
            return new Baseline(value, rs.getInt("n_ref"));
        },
                sensor, week, refFrom, refTo, excludeYear,
                bbox.minLat(), bbox.maxLat(),
                bbox.minLon(), bbox.maxLon());
    }

    /**
     * calendar_weekly tiene 53 semanas por celda, asi que la semana 1
     * cuenta exactamente una fila por celda. Evita count(DISTINCT grid_id).
     */
    public int nCellsAoi(Bbox bbox) {
        if (bbox == null) {
            Integer value = jdbc.queryForObject("""
                    SELECT count(*)::int
                    FROM calendar_weekly
                    WHERE week_of_year = 1
                    """, Integer.class);
            return value == null ? 0 : value;
        }
        Integer value = jdbc.queryForObject("""
                SELECT count(*)::int
                FROM calendar_weekly
                WHERE week_of_year = 1
                  AND cell_lat BETWEEN ? AND ?
                  AND cell_lon BETWEEN ? AND ?
                """, Integer.class,
                bbox.minLat(), bbox.maxLat(),
                bbox.minLon(), bbox.maxLon());
        return value == null ? 0 : value;
    }

    public WeekFlags weekFlags(int year, int week, Bbox bbox) {
        return jdbc.query("""
                SELECT count(*)::int AS rows,
                       coalesce(bool_or(has_nrt), false) AS has_nrt
                FROM grid_weekly
                WHERE isoyear = ?
                  AND week_of_year = ?
                  AND cell_lat BETWEEN ? AND ?
                  AND cell_lon BETWEEN ? AND ?
                """, rs -> {
            if (!rs.next()) {
                return new WeekFlags(0, false);
            }
            return new WeekFlags(rs.getInt("rows"), rs.getBoolean("has_nrt"));
        },
                year, week,
                bbox.minLat(), bbox.maxLat(),
                bbox.minLon(), bbox.maxLon());
    }

    /**
     * Anios con cobertura para una semana ISO y sensor. {@code coverage_daily}
     * es un proxy regional (AR): cada fila representa un dia con al menos una
     * deteccion, no cobertura real por nubosidad/overpass.
     */
    public Integer nDataYears(int week, String sensor) {
        Integer value = jdbc.queryForObject("""
                SELECT count(DISTINCT extract(isoyear FROM acq_date))::int
                FROM coverage_daily
                WHERE has_data
                  AND sensor = ?
                  AND extract(week FROM acq_date) = ?
                """, Integer.class, sensor, week);
        return value == null ? 0 : value;
    }

    public boolean hasCoverage(int year, int week, String sensor) {
        Boolean value = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM coverage_daily
                    WHERE has_data
                      AND sensor = ?
                      AND extract(isoyear FROM acq_date) = ?
                      AND extract(week FROM acq_date) = ?
                )
                """, Boolean.class, sensor, year, week);
        return Boolean.TRUE.equals(value);
    }

    public boolean coverageKnown() {
        Boolean value = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM coverage_daily WHERE has_data)",
                Boolean.class);
        return Boolean.TRUE.equals(value);
    }

    /**
     * Meses (clave {@code anio-mes}, mes base 0) con cobertura para el sensor.
     * Con {@code ALL} se toma la union de sensores.
     */
    public Set<String> coverageMonths(LocalDate from, LocalDate to, String sensor) {
        String sql = """
                SELECT DISTINCT extract(year FROM acq_date)::int AS year,
                                (extract(month FROM acq_date)::int - 1) AS month
                FROM coverage_daily
                WHERE has_data
                  AND acq_date BETWEEN ? AND ?
                """
                + ("ALL".equals(sensor) ? "" : "  AND sensor = ?\n");
        List<Object> params = new ArrayList<>();
        params.add(java.sql.Date.valueOf(from));
        params.add(java.sql.Date.valueOf(to));
        if (!"ALL".equals(sensor)) {
            params.add(sensor);
        }
        return new HashSet<>(jdbc.query(
                sql,
                (rs, rowNum) -> rs.getInt("year") + "-" + rs.getInt("month"),
                params.toArray()));
    }

    public int detectionCellDays(
            String sensor,
            LocalDate from,
            LocalDate to,
            Bbox bbox) {
        Integer value = jdbc.queryForObject("""
                SELECT count(*)::int
                FROM grid_daily
                WHERE day BETWEEN ? AND ?
                  AND cell_lat BETWEEN ? AND ?
                  AND cell_lon BETWEEN ? AND ?
                  AND CAST(? AS TEXT) = ANY(sensors)
                """, Integer.class,
                java.sql.Date.valueOf(from),
                java.sql.Date.valueOf(to),
                bbox.minLat(), bbox.maxLat(),
                bbox.minLon(), bbox.maxLon(),
                sensor);
        return value == null ? 0 : value;
    }

    public List<DailyRow> daily(
            Bbox bbox,
            LocalDate from,
            LocalDate to,
            String sensor,
            int limit) {
        String sql = """
                SELECT day, grid_id, cell_lat, cell_lon, fire_day, n_detections, sensors, frp_max_mw
                FROM grid_daily
                WHERE day BETWEEN ? AND ?
                  AND cell_lat BETWEEN ? AND ?
                  AND cell_lon BETWEEN ? AND ?
                """
                + ("ALL".equals(sensor) ? "" : "  AND CAST(? AS TEXT) = ANY(sensors)\n")
                + "ORDER BY day, grid_id\nLIMIT ?";
        List<Object> params = new ArrayList<>();
        params.add(java.sql.Date.valueOf(from));
        params.add(java.sql.Date.valueOf(to));
        params.add(bbox.minLat());
        params.add(bbox.maxLat());
        params.add(bbox.minLon());
        params.add(bbox.maxLon());
        if (!"ALL".equals(sensor)) {
            params.add(sensor);
        }
        params.add(limit);
        return jdbc.query(sql, (rs, rowNum) -> new DailyRow(
                rs.getObject("day", LocalDate.class),
                rs.getString("grid_id"),
                round(rs.getDouble("cell_lat"), 4),
                round(rs.getDouble("cell_lon"), 4),
                rs.getBoolean("fire_day"),
                rs.getInt("n_detections"),
                toList(rs.getArray("sensors")),
                0,
                Map.of(),
                toDouble(rs, "frp_max_mw"),
                null,
                null,
                "observed"), params.toArray());
    }

    /**
     * Hotspots armonizados por celda-dia y sensor. Misma grilla que
     * {@code grid_daily}: 0,05 grados.
     */
    public List<DailyHotspot> dailyHotspots(
            Bbox bbox,
            LocalDate from,
            LocalDate to,
            String sensor) {
        String sql = """
                SELECT acq_date AS day,
                       '0.05:' || CAST(round(latitude / 0.05) AS BIGINT) || ':' ||
                       CAST(round(longitude / 0.05) AS BIGINT) AS grid_id,
                       sensor,
                       count(*)::int AS hotspots
                FROM v_detections_analysis
                WHERE acq_date BETWEEN ? AND ?
                  AND latitude BETWEEN ? AND ?
                  AND longitude BETWEEN ? AND ?
                  AND
                  """
                + HOTSPOT_PREDICATE
                + ("ALL".equals(sensor) ? "" : "  AND sensor = ?\n")
                + "GROUP BY 1, 2, 3\nORDER BY 1, 2, 3";
        List<Object> params = new ArrayList<>();
        params.add(java.sql.Date.valueOf(from));
        params.add(java.sql.Date.valueOf(to));
        params.add(bbox.minLat());
        params.add(bbox.maxLat());
        params.add(bbox.minLon());
        params.add(bbox.maxLon());
        if (!"ALL".equals(sensor)) {
            params.add(sensor);
        }
        return jdbc.query(sql, (rs, rowNum) -> new DailyHotspot(
                rs.getObject("day", LocalDate.class),
                rs.getString("grid_id"),
                rs.getString("sensor"),
                rs.getInt("hotspots")), params.toArray());
    }

    public List<HeatmapCell> heatmap(
            Bbox bbox,
            LocalDate from,
            LocalDate to,
            String sensor,
            int limit) {
        String era = riskEra(sensor, to);
        if (era == null) {
            return heatmapWithoutRisk(bbox, from, to, sensor, limit);
        }

        String sql = """
                WITH semanas AS (
                    SELECT DISTINCT extract(week FROM d)::smallint AS week_of_year
                    FROM generate_series(?::date, ?::date, '1 day') AS d
                ),
                priors AS (
                    SELECT coalesce(sum(alpha), 0)::float8 AS alpha,
                           coalesce(sum(beta), 0)::float8  AS beta
                    FROM risk_prior_week
                    WHERE era = ?
                      AND week_of_year IN (SELECT week_of_year FROM semanas)
                ),
                risk AS (
                    SELECT grid_id,
                           sum(fire_years)::int AS k,
                           sum(ref_years)::int  AS n,
                           min(ref_years)::int  AS n_min
                    FROM risk_cell_week
                    WHERE era = ?
                      AND week_of_year IN (SELECT week_of_year FROM semanas)
                      AND cell_lat BETWEEN ? AND ?
                      AND cell_lon BETWEEN ? AND ?
                    GROUP BY grid_id
                )
                SELECT c.grid_id, c.cell_lat, c.cell_lon,
                       c.fire_cells, c.fire_days, c.n_detections,
                       r.k, r.n, r.n_min, p.alpha, p.beta
                FROM (
                    SELECT grid_id, cell_lat, cell_lon,
                           CASE WHEN count(*) FILTER (WHERE fire_day) > 0 THEN 1 ELSE 0 END AS fire_cells,
                           count(*) FILTER (WHERE fire_day)::int AS fire_days,
                           coalesce(sum(n_detections), 0)::int AS n_detections
                    FROM grid_daily
                    WHERE day BETWEEN ? AND ?
                      AND cell_lat BETWEEN ? AND ?
                      AND cell_lon BETWEEN ? AND ?
                """
                + ("ALL".equals(sensor) ? "" : "  AND CAST(? AS TEXT) = ANY(sensors)\n")
                + """
                    GROUP BY grid_id, cell_lat, cell_lon
                    ORDER BY fire_days DESC, n_detections DESC
                    LIMIT ?
                ) c
                LEFT JOIN risk r ON r.grid_id = c.grid_id
                CROSS JOIN priors p
                ORDER BY c.fire_days DESC, c.n_detections DESC
                """;
        List<Object> params = new ArrayList<>();
        params.add(java.sql.Date.valueOf(from));
        params.add(java.sql.Date.valueOf(to));
        params.add(era);
        params.add(era);
        params.add(bbox.minLat());
        params.add(bbox.maxLat());
        params.add(bbox.minLon());
        params.add(bbox.maxLon());
        params.add(java.sql.Date.valueOf(from));
        params.add(java.sql.Date.valueOf(to));
        params.add(bbox.minLat());
        params.add(bbox.maxLat());
        params.add(bbox.minLon());
        params.add(bbox.maxLon());
        if (!"ALL".equals(sensor)) {
            params.add(sensor);
        }
        params.add(limit);
        return jdbc.query(sql, (rs, rowNum) -> {
            double alpha = rs.getDouble("alpha");
            double beta = rs.getDouble("beta");
            int k = rs.getInt("k");
            boolean hasRisk = !rs.wasNull();
            int n = rs.getInt("n");
            int nMin = rs.getInt("n_min");
            boolean hasMin = !rs.wasNull();

            Double probability = null;
            Double low = null;
            Double high = null;
            Integer refYears = null;
            String reliability = "insufficient";
            if (hasRisk && n > 0) {
                probability = RiskMath.posterior(k, n, alpha, beta);
                double[] interval = RiskMath.wilson(k, n);
                if (interval != null) {
                    low = interval[0];
                    high = interval[1];
                }
                if (hasMin) {
                    refYears = nMin;
                    reliability = RiskMath.reliability(nMin);
                }
            }

            Double probabilityBase = alpha + beta > 0 ? alpha / (alpha + beta) : null;
            Double anomaly = probability != null && probabilityBase != null && probabilityBase > 0
                    ? round(probability / probabilityBase, 2)
                    : null;

            return new HeatmapCell(
                    rs.getString("grid_id"),
                    round(rs.getDouble("cell_lat"), 4),
                    round(rs.getDouble("cell_lon"), 4),
                    rs.getInt("fire_cells"),
                    rs.getInt("fire_days"),
                    rs.getInt("n_detections"),
                    0,
                    Map.of(),
                    anomaly,
                    "observed",
                    probability,
                    low,
                    high,
                    refYears,
                    reliability);
        }, params.toArray());
    }

    private List<HeatmapCell> heatmapWithoutRisk(
            Bbox bbox,
            LocalDate from,
            LocalDate to,
            String sensor,
            int limit) {
        String sql = """
                SELECT grid_id, cell_lat, cell_lon,
                       CASE WHEN count(*) FILTER (WHERE fire_day) > 0 THEN 1 ELSE 0 END AS fire_cells,
                       count(*) FILTER (WHERE fire_day)::int AS fire_days,
                       coalesce(sum(n_detections), 0)::int AS n_detections
                FROM grid_daily
                WHERE day BETWEEN ? AND ?
                  AND cell_lat BETWEEN ? AND ?
                  AND cell_lon BETWEEN ? AND ?
                """
                + ("ALL".equals(sensor) ? "" : "  AND CAST(? AS TEXT) = ANY(sensors)\n")
                + "GROUP BY grid_id, cell_lat, cell_lon\n"
                + "ORDER BY fire_days DESC, n_detections DESC\n"
                + "LIMIT ?";
        List<Object> params = new ArrayList<>();
        params.add(java.sql.Date.valueOf(from));
        params.add(java.sql.Date.valueOf(to));
        params.add(bbox.minLat());
        params.add(bbox.maxLat());
        params.add(bbox.minLon());
        params.add(bbox.maxLon());
        if (!"ALL".equals(sensor)) {
            params.add(sensor);
        }
        params.add(limit);
        return jdbc.query(sql, (rs, rowNum) -> new HeatmapCell(
                rs.getString("grid_id"),
                round(rs.getDouble("cell_lat"), 4),
                round(rs.getDouble("cell_lon"), 4),
                rs.getInt("fire_cells"),
                rs.getInt("fire_days"),
                rs.getInt("n_detections"),
                0,
                Map.of(),
                null,
                "observed",
                null,
                null,
                null,
                null,
                "insufficient"), params.toArray());
    }

    /**
     * Era de riesgo para el mapa: replica {@code resolvePlan} sobre la fecha
     * final del rango. Las eras NOAA-20/21 no tienen tabla de riesgo en el MVP.
     */
    private String riskEra(String sensor, LocalDate to) {
        return switch (sensor) {
            case "MODIS" -> "MODIS";
            case "VIIRS_SNPP" -> "SNPP";
            case "ALL" -> to.getYear() >= 2013 ? "SNPP" : "MODIS";
            default -> null;
        };
    }

    /**
     * Hotspots armonizados por celda y sensor para el mapa. Misma grilla que
     * {@code grid_daily}: 0,05 grados.
     */
    public List<HeatmapHotspot> heatmapHotspots(
            Bbox bbox,
            LocalDate from,
            LocalDate to,
            String sensor) {
        String sql = """
                SELECT '0.05:' || CAST(round(latitude / 0.05) AS BIGINT) || ':' ||
                       CAST(round(longitude / 0.05) AS BIGINT) AS grid_id,
                       sensor,
                       count(*)::int AS hotspots
                FROM v_detections_analysis
                WHERE acq_date BETWEEN ? AND ?
                  AND latitude BETWEEN ? AND ?
                  AND longitude BETWEEN ? AND ?
                  AND
                  """
                + HOTSPOT_PREDICATE
                + ("ALL".equals(sensor) ? "" : "  AND sensor = ?\n")
                + "GROUP BY 1, 2\nORDER BY 1, 2";
        List<Object> params = new ArrayList<>();
        params.add(java.sql.Date.valueOf(from));
        params.add(java.sql.Date.valueOf(to));
        params.add(bbox.minLat());
        params.add(bbox.maxLat());
        params.add(bbox.minLon());
        params.add(bbox.maxLon());
        if (!"ALL".equals(sensor)) {
            params.add(sensor);
        }
        return jdbc.query(sql, (rs, rowNum) -> new HeatmapHotspot(
                rs.getString("grid_id"),
                rs.getString("sensor"),
                rs.getInt("hotspots")), params.toArray());
    }

    /**
     * Agregacion mensual de actividad.
     *
     * <p>Cuando {@code sensor} no es {@code ALL}, se filtran las filas de
     * {@code grid_daily} donde ese sensor participo. {@code fire_day} sigue
     * siendo all-sensor: {@code fire_cells(sensor)} significa "celdas con
     * actividad en dias en que ese sensor tuvo al menos una deteccion", la
     * misma semantica que usan {@code daily} y {@code heatmap}. No es un
     * conteo de celdas atribuible exclusivamente al sensor.</p>
     */
    public List<MonthFire> monthlyFire(Bbox bbox, LocalDate from, LocalDate to, String sensor) {
        String sql = """
                SELECT extract(year FROM day)::int AS year,
                       (extract(month FROM day)::int - 1) AS month,
                       count(DISTINCT grid_id) FILTER (WHERE fire_day)::int AS fire_cells,
                       count(*) FILTER (WHERE fire_day)::int AS fire_days,
                       coalesce(sum(n_detections), 0)::int AS n_detections
                FROM grid_daily
                WHERE day BETWEEN ? AND ?
                  AND cell_lat BETWEEN ? AND ?
                  AND cell_lon BETWEEN ? AND ?
                """
                + ("ALL".equals(sensor) ? "" : "  AND CAST(? AS TEXT) = ANY(sensors)\n")
                + "GROUP BY 1, 2\nORDER BY 1, 2";
        List<Object> params = new ArrayList<>();
        params.add(java.sql.Date.valueOf(from));
        params.add(java.sql.Date.valueOf(to));
        params.add(bbox.minLat());
        params.add(bbox.maxLat());
        params.add(bbox.minLon());
        params.add(bbox.maxLon());
        if (!"ALL".equals(sensor)) {
            params.add(sensor);
        }
        return jdbc.query(sql, (rs, rowNum) -> new MonthFire(
                rs.getInt("year"),
                rs.getInt("month"),
                rs.getInt("fire_cells"),
                rs.getInt("fire_days"),
                rs.getInt("n_detections")), params.toArray());
    }

    public List<MonthSensorDetections> monthlyDetections(
            Bbox bbox,
            LocalDate from,
            LocalDate to,
            String sensor) {
        String sql = """
                SELECT extract(year FROM acq_date)::int AS year,
                       (extract(month FROM acq_date)::int - 1) AS month,
                       sensor,
                       count(*)::int AS n_detections
                FROM detections
                WHERE acq_date BETWEEN ? AND ?
                  AND latitude BETWEEN ? AND ?
                  AND longitude BETWEEN ? AND ?
                """
                + ("ALL".equals(sensor) ? "" : "  AND sensor = ?\n")
                + "GROUP BY 1, 2, 3\nORDER BY 1, 2, 3";
        List<Object> params = new ArrayList<>();
        params.add(java.sql.Date.valueOf(from));
        params.add(java.sql.Date.valueOf(to));
        params.add(bbox.minLat());
        params.add(bbox.maxLat());
        params.add(bbox.minLon());
        params.add(bbox.maxLon());
        if (!"ALL".equals(sensor)) {
            params.add(sensor);
        }
        return jdbc.query(sql, (rs, rowNum) -> new MonthSensorDetections(
                rs.getInt("year"),
                rs.getInt("month"),
                rs.getString("sensor"),
                rs.getInt("n_detections")), params.toArray());
    }

    /**
     * Hotspots armonizados por mes y sensor. Es la metrica del calendario;
     * {@code nDetections} sigue siendo el conteo crudo.
     */
    public List<MonthlyHotspot> monthlyHotspots(
            Bbox bbox,
            LocalDate from,
            LocalDate to,
            String sensor) {
        String sql = """
                SELECT extract(year FROM acq_date)::int AS year,
                       (extract(month FROM acq_date)::int - 1) AS month,
                       sensor,
                       count(*)::int AS hotspots
                FROM v_detections_analysis
                WHERE acq_date BETWEEN ? AND ?
                  AND latitude BETWEEN ? AND ?
                  AND longitude BETWEEN ? AND ?
                  AND
                  """
                + HOTSPOT_PREDICATE
                + ("ALL".equals(sensor) ? "" : "  AND sensor = ?\n")
                + "GROUP BY 1, 2, 3\nORDER BY 1, 2, 3";
        List<Object> params = new ArrayList<>();
        params.add(java.sql.Date.valueOf(from));
        params.add(java.sql.Date.valueOf(to));
        params.add(bbox.minLat());
        params.add(bbox.maxLat());
        params.add(bbox.minLon());
        params.add(bbox.maxLon());
        if (!"ALL".equals(sensor)) {
            params.add(sensor);
        }
        return jdbc.query(sql, (rs, rowNum) -> new MonthlyHotspot(
                rs.getInt("year"),
                rs.getInt("month"),
                rs.getString("sensor"),
                rs.getInt("hotspots")), params.toArray());
    }

    public List<WeeklyExportRow> weeklyExport(
            Bbox bbox,
            int fromYear,
            int fromWeek,
            int toYear,
            int toWeek,
            String sensor,
            int limit) {
        String sql = """
                SELECT isoyear, week_of_year, grid_id, cell_lat, cell_lon, sensor,
                       fire_cells, fire_days, n_detections, frp_sum_mw
                FROM grid_weekly_sensor
                WHERE (isoyear > ? OR (isoyear = ? AND week_of_year >= ?))
                  AND (isoyear < ? OR (isoyear = ? AND week_of_year <= ?))
                  AND cell_lat BETWEEN ? AND ?
                  AND cell_lon BETWEEN ? AND ?
                """
                + ("ALL".equals(sensor) ? "" : "  AND sensor = ?\n")
                + "ORDER BY isoyear, week_of_year, grid_id, sensor\nLIMIT ?";
        List<Object> params = new ArrayList<>();
        params.add(fromYear);
        params.add(fromYear);
        params.add(fromWeek);
        params.add(toYear);
        params.add(toYear);
        params.add(toWeek);
        params.add(bbox.minLat());
        params.add(bbox.maxLat());
        params.add(bbox.minLon());
        params.add(bbox.maxLon());
        if (!"ALL".equals(sensor)) {
            params.add(sensor);
        }
        params.add(limit);
        return jdbc.query(sql, (rs, rowNum) -> new WeeklyExportRow(
                rs.getInt("isoyear"),
                rs.getInt("week_of_year"),
                rs.getString("grid_id"),
                round(rs.getDouble("cell_lat"), 4),
                round(rs.getDouble("cell_lon"), 4),
                rs.getString("sensor"),
                rs.getInt("fire_cells"),
                rs.getInt("fire_days"),
                rs.getInt("n_detections"),
                toDouble(rs, "frp_sum_mw")), params.toArray());
    }

    public List<AnnualSensor> annualBySensor(Bbox bbox, int fromYear, int toYear) {
        return jdbc.query("""
                SELECT isoyear,
                       sensor,
                       coalesce(sum(fire_cells), 0)::int AS fire_cells
                FROM grid_weekly_sensor
                WHERE isoyear BETWEEN ? AND ?
                  AND cell_lat BETWEEN ? AND ?
                  AND cell_lon BETWEEN ? AND ?
                GROUP BY isoyear, sensor
                ORDER BY isoyear, sensor
                """, (rs, rowNum) -> new AnnualSensor(
                rs.getInt("isoyear"),
                rs.getString("sensor"),
                rs.getInt("fire_cells")),
                fromYear, toYear,
                bbox.minLat(), bbox.maxLat(),
                bbox.minLon(), bbox.maxLon());
    }

    private Double toDouble(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        float value = rs.getFloat(column);
        return rs.wasNull() ? null : round(value, 3);
    }

    private double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private List<String> toList(Array array) {
        if (array == null) {
            return List.of();
        }
        try {
            String[] values = (String[]) array.getArray();
            return values == null ? List.of() : Arrays.asList(values);
        } catch (java.sql.SQLException ex) {
            return List.of();
        }
    }

    public record SensorWeek(
            String sensor,
            int fireCells,
            int fireDays,
            int nDetections,
            Double frpSum
    ) {
    }

    public record Baseline(Double median, int nRef) {
    }

    public record WeekFlags(int rows, boolean hasNrt) {
    }

    public record MonthFire(
            int year,
            int month,
            int fireCells,
            int fireDays,
            int nDetections
    ) {
    }

    public record MonthSensorDetections(
            int year,
            int month,
            String sensor,
            int nDetections
    ) {
    }

    public record MonthlyHotspot(
            int year,
            int month,
            String sensor,
            int hotspots
    ) {
    }

    public record DailyHotspot(
            LocalDate day,
            String gridId,
            String sensor,
            int hotspots
    ) {
    }

    public record HeatmapHotspot(
            String gridId,
            String sensor,
            int hotspots
    ) {
    }

    public record AnnualSensor(int year, String sensor, int fireCells) {
    }

    public record WeeklyExportRow(
            int isoyear,
            int weekOfYear,
            String gridId,
            double cellLat,
            double cellLon,
            String sensor,
            int fireCells,
            int fireDays,
            int nDetections,
            Double frpSumMw
    ) {
    }
}
