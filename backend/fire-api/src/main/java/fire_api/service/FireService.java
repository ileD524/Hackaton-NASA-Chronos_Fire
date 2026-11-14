package fire_api.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import fire_api.dto.CalendarItem;
import fire_api.dto.CalendarResponse;
import fire_api.dto.FireSummaryResponse;
import fire_api.dto.RadiusFireItem;
import fire_api.dto.RadiusRequest;
import fire_api.dto.RadiusResponse;

/**
 * Contiene la lógica que actualmente usa datos simulados.
 *
 * Cuando la base esté lista, este servicio conservará el contrato público
 * y reemplazará estos métodos por llamadas al repositorio PostgreSQL.
 */
@Service
public class FireService {

    public CalendarResponse getCalendar(LocalDate startDate, LocalDate endDate) {
        List<CalendarItem> items = List.of(
                new CalendarItem(2024, 1, 2, 45.3, 0.4, "good"),
                new CalendarItem(2024, 2, 5, 120.8, 1.6, "good"),
                new CalendarItem(2024, 3, 1, 12.2, -0.5, "good"),
                new CalendarItem(2025, 1, 4, 93.7, 1.1, "good")
        );

        return new CalendarResponse(
                "Argentina",
                startDate,
                endDate,
                "WEEK",
                "mock-v1",
                items
        );
    }

    public FireSummaryResponse getSummary(LocalDate startDate, LocalDate endDate) {
        return new FireSummaryResponse(
                "Argentina",
                startDate,
                endDate,
                8,
                6,
                272.0,
                LocalDate.of(2024, 2, 10),
                1.6,
                "good"
        );
    }

    public RadiusResponse getByRadius(RadiusRequest request) {
        List<RadiusFireItem> items = List.of(
                new RadiusFireItem(
                        request.startDate(),
                        "0.05:-692:-1168",
                        request.latitude(),
                        request.longitude(),
                        true,
                        40.2,
                        90,
                        List.of("VIIRS_SNPP", "VIIRS_NOAA20")
                )
        );

        return new RadiusResponse(
                request.latitude(),
                request.longitude(),
                request.radiusKm(),
                request.startDate(),
                request.endDate(),
                items
        );
    }
}
