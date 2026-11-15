package com.chronosfire.backend.controller;

import com.chronosfire.backend.dto.Aoi;
import com.chronosfire.backend.service.AoiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "AOIs")
public class AoiController {

    private final AoiService aoiService;

    public AoiController(AoiService aoiService) {
        this.aoiService = aoiService;
    }

    @GetMapping("/aois")
    @Operation(summary = "Catalogo de AOIs y ventanas de evento")
    public List<Aoi> aois() {
        return aoiService.getAois();
    }
}
