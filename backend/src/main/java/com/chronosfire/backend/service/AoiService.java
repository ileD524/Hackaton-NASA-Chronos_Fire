package com.chronosfire.backend.service;

import com.chronosfire.backend.dto.Aoi;
import com.chronosfire.backend.dto.AoiWindow;
import com.chronosfire.backend.repository.AoiRepository;
import com.chronosfire.backend.repository.AoiRepository.AoiRow;
import com.chronosfire.backend.repository.AoiRepository.WindowRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AoiService {

    private final AoiRepository repository;

    public AoiService(AoiRepository repository) {
        this.repository = repository;
    }

    public List<Aoi> getAois() {
        Map<String, List<AoiWindow>> windows = new LinkedHashMap<>();
        for (WindowRow row : repository.findWindows()) {
            windows.computeIfAbsent(row.aoiId(), key -> new ArrayList<>())
                    .add(new AoiWindow(
                            row.isoyear(),
                            row.weekFrom(),
                            row.weekTo(),
                            row.expectedPeak()));
        }
        for (List<AoiWindow> values : windows.values()) {
            values.sort(Comparator.comparingInt(AoiWindow::isoyear)
                    .thenComparingInt(AoiWindow::weekFrom));
        }

        List<Aoi> result = new ArrayList<>();
        for (AoiRow row : repository.findAois()) {
            result.add(new Aoi(
                    row.aoiId(),
                    row.name(),
                    row.minLat(),
                    row.minLon(),
                    row.maxLat(),
                    row.maxLon(),
                    row.observedSensor(),
                    row.baselineSensor(),
                    row.refFrom(),
                    row.refTo(),
                    row.status(),
                    row.notes(),
                    windows.getOrDefault(row.aoiId(), List.of())));
        }
        return result;
    }
}
