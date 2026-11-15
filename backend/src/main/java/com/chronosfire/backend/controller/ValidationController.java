package com.chronosfire.backend.controller;

import com.chronosfire.backend.dto.ValidationEvent;
import com.chronosfire.backend.service.ValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Validacion")
public class ValidationController {

    private final ValidationService validationService;

    public ValidationController(ValidationService validationService) {
        this.validationService = validationService;
    }

    @GetMapping("/validation")
    @Operation(summary = "Eventos conocidos y sus metricas de anomalia")
    public List<ValidationEvent> validation() {
        return validationService.getValidation();
    }
}
