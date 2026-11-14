package fire_api.dto;

import java.time.LocalDate;
import java.util.List;

public record RadiusFireItem(
        LocalDate date,
        String gridId,
        double latitude,
        double longitude,
        boolean fireDay,
        double frpSumMw,
        int confidenceMax,
        List<String> sensors
) {
}
