package com.chronosfire.backend.controller;

import com.chronosfire.backend.dto.DailyRow;
import com.chronosfire.backend.dto.HeatmapCell;
import com.chronosfire.backend.dto.MonthlyCell;
import com.chronosfire.backend.dto.TrendRow;
import com.chronosfire.backend.dto.WeeklyResponse;
import com.chronosfire.backend.service.CalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Calendario")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping("/calendar/weekly")
    @Operation(summary = "Actividad semanal por AOI, baseline y anomalia")
    public WeeklyResponse weekly(
            @RequestParam(required = false) String bbox,
            @RequestParam int year,
            @RequestParam int week,
            @RequestParam(defaultValue = "AUTO") String sensor) {
        return calendarService.getWeekly(bbox, year, week, sensor);
    }

    @GetMapping("/calendar/daily")
    @Operation(summary = "Serie diaria de celdas con actividad")
    public List<DailyRow> daily(
            @RequestParam(required = false) String bbox,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "ALL") String sensor,
            @RequestParam(defaultValue = "5000") int limit) {
        return calendarService.getDaily(bbox, from, to, sensor, limit);
    }

    @GetMapping("/calendar/heatmap")
    @Operation(summary = "Celdas de 0,05 grados para el mapa")
    public List<HeatmapCell> heatmap(
            @RequestParam(required = false) String bbox,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "ALL") String sensor,
            @RequestParam(defaultValue = "5000") int limit) {
        return calendarService.getHeatmap(bbox, from, to, sensor, limit);
    }

    @GetMapping("/calendar/monthly")
    @Operation(summary = "Agregacion mensual para la UI")
    public List<MonthlyCell> monthly(
            @RequestParam(required = false) String bbox,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "ALL") String sensor) {
        return calendarService.getMonthly(bbox, from, to, sensor);
    }

    @GetMapping("/calendar/trend")
    @Operation(summary = "Serie anual con baseline y anomalia")
    public List<TrendRow> trend(
            @RequestParam(required = false) String bbox,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "AUTO") String sensor) {
        return calendarService.getTrend(bbox, from, to, sensor);
    }

    @GetMapping("/calendar/export.csv")
    @Operation(summary = "Exporta actividad semanal en CSV")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String bbox,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "ALL") String sensor,
            @RequestParam(defaultValue = "50000") int limit) {
        String csv = calendarService.exportCsv(bbox, from, to, sensor, limit);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=utf-8")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("chronosfire-calendario-semanal.csv")
                                .build()
                                .toString())
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
