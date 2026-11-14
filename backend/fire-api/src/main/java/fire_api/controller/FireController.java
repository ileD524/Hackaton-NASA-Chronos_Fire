package fire_api.controller;

import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import fire_api.dto.CalendarResponse;
import fire_api.dto.FireSummaryResponse;
import fire_api.dto.RadiusRequest;
import fire_api.dto.RadiusResponse;
import fire_api.service.FireService;

@RestController
@RequestMapping("/api/fire")
@CrossOrigin(origins = "http://localhost:5173")
public class FireController {

    private final FireService fireService;

    public FireController(FireService fireService) {
        this.fireService = fireService;
    }

    @GetMapping("/calendar")
    public CalendarResponse calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return fireService.getCalendar(startDate, endDate);
    }

    @GetMapping("/summary")
    public FireSummaryResponse summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return fireService.getSummary(startDate, endDate);
    }

    /**
     * Se usa POST porque el filtro tiene varios campos y luego crecerá
     * con sensores, tipo de período y opciones de calidad.
     */
    @PostMapping("/radius")
    @ResponseStatus(HttpStatus.OK)
    public RadiusResponse radius(@Valid @RequestBody RadiusRequest request) {
        validateDateRange(request.startDate(), request.endDate());
        return fireService.getByRadius(request);
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate debe ser posterior o igual a startDate");
        }
    }
}
