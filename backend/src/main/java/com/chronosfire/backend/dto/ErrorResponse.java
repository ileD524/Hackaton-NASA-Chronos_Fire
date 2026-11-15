package com.chronosfire.backend.dto;

public record ErrorResponse(String code, String message, String requestId) {
}
