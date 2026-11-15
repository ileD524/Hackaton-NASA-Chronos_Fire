package com.chronosfire.backend.repository;

import com.chronosfire.backend.dto.Bbox;
import com.chronosfire.backend.dto.DailyRow;
import com.chronosfire.backend.dto.HeatmapCell;
import java.sql.Array;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CalendarRepository {

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
                toDouble(rs, "frp_max_mw"),
                null,
                null,
                "observed"), params.toArray());
    }

    public List<HeatmapCell> heatmap(
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
                null,
                "observed"), params.toArray());
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
