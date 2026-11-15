# Funcionalidad e instalación - ChronosFire

Demo local del calendario de actividad de fuego para Argentina. El navegador
consume un backend **Java/Spring Boot** de solo lectura sobre PostgreSQL 17
(`chronos_fire`). No requiere clave de FIRMS ni un servidor Python. No es un
servicio operativo de alertas.

## Requisitos

- Java 21 o superior.
- Maven 3.9 o superior.
- Node.js 22.18 o superior (o 24.12+).
- PostgreSQL 17 con la base `chronos_fire` cargada.

## Levantar la demo

Desde la raíz del repositorio:

```powershell
Copy-Item backend\.env.example backend\.env
# editar DB_PASSWORD en backend\.env
.\run-app.ps1
```

Abrir:

```text
Frontend: http://127.0.0.1:5173
Backend:  http://127.0.0.1:8080
Swagger:  http://127.0.0.1:8080/swagger-ui.html
```

También se puede usar la task de VS Code **app: dev**. El arranque manual está
en [README-LOCAL.md](../README-LOCAL.md).

## Qué se puede explorar

- **Filtros:** área por rectángulo (lat/lon), presets (Delta del Paraná,
  Córdoba, Corrientes) y rango de fechas. La vista inicial es el Delta para
  evitar la consulta nacional pesada.
- **Calendario mensual:** cada celda es un mes y muestra celdas de 0,05° con
  actividad. La vista ampliada comparte la selección. El asterisco indica mes
  parcial y el guion indica dato faltante.
- **Comparación histórica:** diferencia contra la mediana del mismo mes en
  otros años completos, con años de referencia y confianza calculados por el
  backend.
- **Mapa:** celdas de 0,05° con hotspots del mes. El popup muestra el total y
  el desglose por sensor. El tamaño codifica días con actividad.
- **Detalle diario:** gráfico y tabla de celdas con actividad del mes.
- **Alertas NRT:** últimas detecciones de la ventana configurada, agrupadas por
  celda, con exclusión de focos industriales.
- **Validación:** eventos históricos conocidos con anomalía y veredicto.
- **Descargas CSV:** calendario semanal y hotspots. El aviso indica si la
  descarga se truncó en el tope de 50.000 filas.

## Qué miden las series

- La unidad es **celda de 0,05° (~5 km)**, no celda de 1 km².
- `fire_day`: celda con al menos un hotspot armonizado en un día UTC.
- `hotspots`: detecciones de vegetación efectiva, confianza nominal/alta y
  fuera de la máscara industrial. **No es cantidad de incendios.**
- `fireCells`: celdas distintas con actividad en el período.
- `ALL` no es un promedio: está dominada por VIIRS. La API permite filtrar por
  sensor; el selector en la UI es trabajo pendiente.

Las definiciones completas están en [docs/DECISIONES.md](../../../docs/DECISIONES.md).

## Límites

- `coverage_daily` está vacía y `n_data_years` es `NULL`: una semana sin filas
  no confirma ausencia de fuego.
- No hay calibración física de eficiencia de detección ni de FRP.
- No hay área quemada ni cantidad de incendios únicos.
- 2026 es NRT provisional y queda fuera del baseline.
- La comparación entre eras es relativa; no es un nivel absoluto calibrado.

## Tests

```powershell
# Backend
cd ..\..\backend
mvn test

# Frontend
cd ..\frontend\ChronosFire
npm run build
npm run test:api
npm run test:browser   # requiere backend y frontend activos
```

El backend incluye tests de controladores, servicios, contrato OpenAPI y un
smoke SQL de datos. El frontend incluye chequeos de contrato y una prueba de
navegador con Chromium.

## Documentación

- Contrato: [backend/CONTRATO_API.md](../../../backend/CONTRATO_API.md)
- OpenAPI: [backend/openapi/chronosfire-api.yaml](../../../backend/openapi/chronosfire-api.yaml)
- Decisiones: [docs/DECISIONES.md](../../../docs/DECISIONES.md)
- Validación: [backend/VALIDACION_6_8.md](../../../backend/VALIDACION_6_8.md)
- Marca: [MARCA.md](MARCA.md)
- Recursos: [ASSETS.md](ASSETS.md)
