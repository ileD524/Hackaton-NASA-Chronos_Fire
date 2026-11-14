package fire_api.dto;

import java.time.LocalDate;

public record FireSummaryResponse(
        String region,
        LocalDate startDate,
        LocalDate endDate,
        long fireDays,
        long activeCells,
        double frpSumMw,
        LocalDate peakDate,
        Double currentAnomalyZscore,
        String dataQuality
) {
}
