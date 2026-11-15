package com.chronosfire.backend.dto;

/**
 * Resultado de una exportacion CSV.
 *
 * @param csv       contenido completo, con BOM UTF-8
 * @param rows      filas de datos incluidas
 * @param limit     tope aplicado por la API
 * @param truncated true si habia mas filas que {@code limit}
 */
public record CsvExport(
        String csv,
        int rows,
        int limit,
        boolean truncated
) {
}
