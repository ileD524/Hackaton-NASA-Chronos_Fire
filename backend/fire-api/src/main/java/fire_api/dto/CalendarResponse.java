package fire_api.dto;

import java.time.LocalDate;
import java.util.List;

public record CalendarResponse(
        String region,
        LocalDate startDate,
        LocalDate endDate,
        String period,
        String algorithmVersion,
        List<CalendarItem> items
) {
}
