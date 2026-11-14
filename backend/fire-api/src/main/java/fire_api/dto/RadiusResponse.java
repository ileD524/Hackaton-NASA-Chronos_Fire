package fire_api.dto;

import java.time.LocalDate;
import java.util.List;

public record RadiusResponse(
        double centerLatitude,
        double centerLongitude,
        double radiusKm,
        LocalDate startDate,
        LocalDate endDate,
        List<RadiusFireItem> items
) {
}
