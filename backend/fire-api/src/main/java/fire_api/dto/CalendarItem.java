package fire_api.dto;

/**
 * Un período del calendario. Por ahora representa una semana.
 * Más adelante sus valores vendrán de calendar_weekly y grid_daily.
 */
public record CalendarItem(
        int year,
        int week,
        int fireDays,
        double frpSumMw,
        Double anomalyZscore,
        String dataQuality
) {
}
