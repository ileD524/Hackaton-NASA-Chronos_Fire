package com.chronosfire.backend.controller;

import com.chronosfire.backend.dto.MetaResponse;
import com.chronosfire.backend.service.MetaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Metadatos")
public class MetaController {

    private final MetaService metaService;

    public MetaController(MetaService metaService) {
        this.metaService = metaService;
    }

    @GetMapping("/meta")
    @Operation(summary = "Cobertura, sensores, conteos, definiciones y limitaciones")
    public MetaResponse meta() {
        return metaService.getMeta();
    }
}
