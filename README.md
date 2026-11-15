# Chronos Fire

Calendario de actividad de fuego para Argentina: MODIS + VIIRS armonizados,
backend Java/Spring Boot y frontend Vue 3.

Grilla de **0,05° (~5 km)**, calendario mensual, hotspots por sensor,
comparación contra la mediana multianual, exportación CSV con aviso de
truncamiento y descarga SVG/PNG del calendario.

## Estado

- 15 endpoints de solo lectura sobre PostgreSQL 17.
- 7.082.474 detecciones armonizadas, 8 pares sensor/fuente.
- Rango de datos: 2000-11-01 a 2026-11-14; la UI carga el archivo disponible.
- Calendario, mapa, alertas NRT, validación de eventos, dos exportaciones CSV y
  descarga SVG/PNG del calendario.
- 48 tests backend (controladores, servicios, OpenAPI y smoke SQL) y pruebas de
  contrato/navegador en el frontend.
- La base se usa en modo lectura: el backend no crea ni modifica tablas.

## Requisitos

- Java 21+
- Maven 3.9+
- Node.js 22.18+ (o 24.12+)
- PostgreSQL 17 con la base `chronos_fire` cargada
- Docker Desktop (opcional, para la demo con dump incluido)

## Demo con Docker (sin dataset completo)

El repo incluye un dump acotado y real para abrir la app sin descargar 7
millones de filas:

```powershell
docker compose up -d
.\run-app.ps1 -Demo
```

El compose levanta PostgreSQL 17 en `127.0.0.1:5433` con la base
`chronos_fire_demo`. Pasos, alcance y reset en `docker/README.md`.
Para el dataset completo, ver `data/README.md`.

## Levantar todo con un comando

1. Crear el archivo local de entorno:

```powershell
Copy-Item backend\.env.example backend\.env
```

2. Editar `backend\.env` y poner la clave real de PostgreSQL:

```text
DB_URL=jdbc:postgresql://127.0.0.1:5432/chronos_fire
DB_USER=postgres
DB_PASSWORD=tu_clave
```

3. Desde la raíz del repo:

```powershell
.\run-app.ps1
```

El script:

- carga `backend\.env`;
- inicia Spring Boot en `:8080`;
- espera `/api/health`;
- inicia Vite en `:5173`;
- al cortar con `Ctrl+C`, detiene el backend que haya iniciado.

Abrir:

```text
Frontend: http://127.0.0.1:5173
Backend:  http://127.0.0.1:8080
Swagger:  http://127.0.0.1:8080/swagger-ui.html
```

## Task de VS Code

El repo incluye `.vscode/tasks.json`:

- `app: dev` → backend + frontend.
- `app: backend` → solo backend.
- `app: frontend` → solo frontend.

Usar **Terminal → Run Task… → app: dev**, o `Ctrl+Shift+B` si está
configurada como build por defecto.

## Arranque manual

Backend:

```powershell
cd backend
$env:DB_PASSWORD = 'tu_clave'
mvn spring-boot:run
```

Frontend, en otra terminal:

```powershell
cd frontend\ChronosFire
npm ci
npm run dev
```

Vite redirige `/api` a `http://127.0.0.1:8080`.

## Tests

Backend:

```powershell
cd backend
mvn test
```

Incluye tests de controladores, servicios, contrato OpenAPI y el smoke SQL de
Delta 2020. El script de datos se puede correr aparte:

```powershell
$env:PGPASSWORD = 'tu_clave'
psql -h 127.0.0.1 -U postgres -d chronos_fire -v ON_ERROR_STOP=1 `
  -f backend\db\validacion\05_smoke_delta_2020.sql
```

Frontend:

```powershell
cd frontend\ChronosFire
npm run build
npm run test:api
npm run test:browser   # requiere backend y frontend activos
npm run test:png       # descarga SVG/PNG y valida contraste; requiere backend y frontend
```

## Métricas y límites

- **Hotspot**: detección FIRMS de vegetación efectiva, confianza nominal/alta y
  fuera de la máscara industrial. No es un incendio único.
- **Celda con actividad (`fire_day`)**: celda de 0,05° con al menos un hotspot
  armonizado en un día UTC.
- **`ALL` no es un promedio**: la serie está dominada por VIIRS. La UI permite
  elegir un sensor o «todos los sensores»; el calendario, el mapa y el detalle
  diario se recalculan.
- **Sin calibración física** de eficiencia de detección ni de FRP.
- **Sin datos no es sin fuego**: `coverage_daily` está vacía.
- **CSV**: máximo 50.000 filas; las detecciones aceptan hasta 10.000 días. La
  UI avisa si la descarga se truncó.

Las definiciones completas están en `docs/DECISIONES.md`.

## Documentación

- Demo Docker: `docker/README.md`
- Datos y ETL: `data/README.md`
- Decisiones y definiciones: `docs/DECISIONES.md`
- Arquitectura y contrato: `backend/CONTRATO_API.md`
- OpenAPI: `backend/openapi/chronosfire-api.yaml`
- Plan de backend: `backend/PLAN_BACKEND.md`
- Armonización: `backend/PLAN_ARMONIZACION.md`
- Validación: `backend/VALIDACION_6_8.md`
- Frontend local: `frontend/ChronosFire/README-LOCAL.md`
- Funcionalidad del frontend: `frontend/ChronosFire/docs/FUNCIONALIDAD.md`
- Brechas y plan de cierre: `INFORME_BRECHAS_Y_PLAN_BACKEND_FRONTEND.md`

La base se usa en modo lectura: el backend no crea ni modifica tablas.
