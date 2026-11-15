package com.chronosfire.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.chronosfire.backend.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void returnsNotFoundForMissingStaticResource() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/favicon.ico");
        request.addHeader("X-Request-Id", "request-123");

        ResponseEntity<ErrorResponse> response = handler.notFound(
                new NoResourceFoundException(HttpMethod.GET, "favicon.ico"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .isEqualTo(new ErrorResponse("not_found", "Recurso no encontrado.", "request-123"));
    }
}
