package com.chronosfire.backend.service;

import com.chronosfire.backend.dto.Aoi;
import com.chronosfire.backend.dto.AoiWindow;
import com.chronosfire.backend.dto.ValidationEvent;
import com.chronosfire.backend.dto.WeeklyResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class ValidationService {

    private final AoiService aoiService;
    private final CalendarService calendarService;

    public ValidationService(AoiService aoiService, CalendarService calendarService) {
        this.aoiService = aoiService;
        this.calendarService = calendarService;
    }

    @Cacheable("validation")
    public List<ValidationEvent> getValidation() {
        List<ValidationEvent> result = new ArrayList<>();
        for (Aoi aoi : aoiService.getAois()) {
            String bbox = bboxString(aoi);
            for (AoiWindow window : aoi.windows()) {
                int week = window.expectedPeak() == null ? window.weekFrom() : window.expectedPeak();
                WeeklyResponse weekly = calendarService.getWeekly(
                        bbox, window.isoyear(), week, aoi.observedSensor());
                result.add(new ValidationEvent(
                        aoi.aoiId(),
                        aoi.name(),
                        window.isoyear(),
                        week,
                        weekly.observedFireCells(),
                        weekly.baselineFireCells(),
                        weekly.referenceYears(),
                        weekly.anomaly(),
                        weekly.confidence(),
                        verdict(weekly)));
            }
        }
        result.sort(Comparator.comparing(ValidationEvent::aoiId)
                .thenComparingInt(ValidationEvent::isoyear)
                .thenComparingInt(ValidationEvent::week));
        return result;
    }

    private String verdict(WeeklyResponse weekly) {
        if ("insufficient".equals(weekly.confidence())) {
            return "insufficient";
        }
        return weekly.anomaly() != null && weekly.anomaly() > 1.0 ? "pass" : "fail";
    }

    private String bboxString(Aoi aoi) {
        return String.format(Locale.ROOT, "%s,%s,%s,%s",
                aoi.minLon(), aoi.minLat(), aoi.maxLon(), aoi.maxLat());
    }
}
