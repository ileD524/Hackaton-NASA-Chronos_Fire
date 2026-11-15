# Chronos Fire

### DEMO ONLINE: https://chronosfire-frontend-production.up.railway.app/ 
Debido a el volúmen de los datos, en la demo online se utilizó una base de datos acortada: 2013 a 2026
Para una mejor experiencia se deberán cargar los datasets a una base de datos local. (Ver sección de base de datos)

Calendario de actividad de fuego para Argentina: MODIS + VIIRS armonizados,
backend Java/Spring Boot y frontend Vue 3.

Grilla de **0,05° (~5 km)**, calendario mensual, hotspots por sensor,
comparación contra la mediana multianual, exportación CSV con aviso de
truncamiento y descarga SVG/PNG del calendario.

Incluye una **capa de frecuencia histórica por celda-semana** (`risk-v1-low`):
probabilidad estimada, intervalo de confianza del 90 %, años de referencia y
percentil de actividad reciente. Es una capa descriptiva de recurrencia, **no
una predicción ni una alerta oficial**.

## Estado

- 15 endpoints de solo lectura sobre PostgreSQL 17.
- 7.082.474 detecciones armonizadas, 8 pares sensor/fuente.
- Rango de datos: 2000-11-01 a 2026-11-14; la UI carga el archivo disponible.
- Calendario, mapa, alertas NRT, validación de eventos, dos exportaciones CSV y
  descarga SVG/PNG del calendario.
- Capa de riesgo materializada: 7.438.232 celdas-semana (70.172 celdas × 53
  semanas × 2 eras), 106 priors y 2 backtests. Se publica en `/api/alerts`,
  `/api/calendar/heatmap` y `/api/meta`.
- Alertas con probabilidad histórica, IC 90 %, `n`, confiabilidad y percentil;
  mapa con probabilidad agregada del rango y panel de metodología con
  calibración (Brier, BSS, AUC).
- 68 tests backend (controladores, servicios, riesgo, OpenAPI y smoke SQL) y
  pruebas de contrato/navegador/PNG en el frontend.
- La base se usa en modo lectura: el backend no crea ni modifica tablas.

## Requisitos

- Java 21+
- Maven 3.9+
- Node.js 22.18+ (o 24.12+)
- PostgreSQL 17 con la base `chronos_fire` cargada. Para las probabilidades
  históricas también hacen falta `risk_cell_week` y `risk_backtest`; sin ellas
  `/api/meta` devuelve `risk: null` y las alertas no pueden calcular esa capa.
- Docker Desktop (opcional, para la demo con dump incluido)

## Capa de riesgo histórico

El cálculo pesado se hace **offline en SQL**; el backend solo lee y aplica
aritmética cerrada en Java. La unidad es **celda de 0,05° × semana ISO × era**
(`MODIS` o `SNPP`). No se mezclan sensores ni eras.

Estado verificado al **15/11/2026**:

| Objeto | Estado verificado |
|---|---|
| `risk_cell_week` | 7.438.232 filas |
| `risk_prior_week` | 106 filas (53 semanas × 2 eras) |
| `coverage_daily` | 19.260 filas (cobertura proxy) |
| `risk_backtest` | 2 filas (holdout principal y control) |
| `model_version` | `risk-v1-low` |
| Backtest 2013–2024 → 2025 | Brier 0,011480 · BSS 0,0286 · AUC 0,740 |
| Control 2019–2024 → 2025 | Brier 0,011543 · BSS 0,0236 · AUC 0,713 |

En la API:

- `/api/alerts` agrega `fireProbability`, `probabilityLow`,
  `probabilityHigh`, `probabilityRefYears`, `probabilityReliability` y
  `activityPercentile` (este último solo con `days = 7`).
- `/api/calendar/heatmap` agrega la probabilidad histórica agregada del rango
  consultado por celda, con IC y `n`.
- `/api/meta` publica el bloque `risk` con la calibración y la puerta de
  calidad.

Reglas de lectura:

- El valor `p` nunca se muestra sin `n` ni sin intervalo. Con
  `reliability = insufficient` se muestra `—` y `p` viaja como `null`.
- La probabilidad del mapa es **semanal agregada del rango**, no mensual ni
  diaria.
- Con sensores NOAA-20/21 no se publican campos de riesgo; las alertas usan la
  era `SNPP` como referencia.
- El BSS 0,0286 está por debajo del umbral 0,05: por eso la etiqueta es
  `risk-v1-low`. **No es una probabilidad calibrada de ocurrencia.**

La especificación, fórmulas y validación completa están en
`informes/INFORME_MODULO_ESTADISTICO_RIESGO.md` y `backend/db/README.md`.

## Carga de datos en PostgreSQL (dataset completo)

El repo no incluye el dataset completo: solo el dump acotado de la demo. El backend es de solo lectura, así que la base `chronos_fire` debe existir y estar cargada antes de levantar la app. Sin datos, el calendario, las alertas y `/api/meta` fallan o devuelven vacío.

> Para ver la interfaz sin descargar datos: `docker compose up -d` y `.\run-app.ps1 -Demo`. Esa demo usa `chronos_fire_demo` en `:5433`, no incluye la capa de riesgo y no reemplaza esta carga.

### Requisitos

- PostgreSQL 17 en `127.0.0.1:5432`.
- `psql` disponible.
- Python 3 con DuckDB: `python -m pip install duckdb`.
- Datos FIRMS descargados según `data/README.md`: JSON en `data/DL_FIRE_*` y CSV NRT en `data/tmp/`.
- Varios GB libres para los archivos y el temporal de DuckDB.

### 1. Crear la base y el esquema

```powershell
$env:PGPASSWORD = 'tu_clave'
$psql = 'C:\Program Files\PostgreSQL\17\bin\psql.exe'
$db = 'chronos_fire'

# Solo la primera vez
& $psql -h 127.0.0.1 -p 5432 -U postgres -d postgres `
  -c "CREATE DATABASE chronos_fire;"

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\ChronosFireDB.sql

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

El dump de demo **no incluye la capa de riesgo** (`risk_cell_week` ni
`risk_backtest`): se generó antes de `risk-v1-low`. Por eso la demo no muestra
calibración en `/api/meta` ni probabilidad histórica en las alertas. Para verla
hay que cargar el dataset completo y correr los scripts de `backend/db/`.

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

Incluye 68 tests de controladores, servicios, riesgo (`RiskMathTest`,
`RiskServiceTest`), contrato OpenAPI y el smoke SQL de Delta 2020. El script de
datos se puede correr aparte:

```powershell
$env:PGPASSWORD = 'tu_clave'
psql -h 127.0.0.1 -U postgres -d chronos_fire -v ON_ERROR_STOP=1 `
  -f backend\db\validacion\05_smoke_delta_2020.sql
```

La capa de riesgo se valida con un script de solo lectura que devuelve 0 filas
cuando todo está bien:

```powershell
psql -h 127.0.0.1 -U postgres -d chronos_fire -v ON_ERROR_STOP=1 `
  -f backend\db\validacion\06_riesgo.sql
```

Los pasos completos de rebuild y backtest están en `backend/db/README.md`.

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
- **Detalle diario por sensor**: para un sensor puntual se exige que ese sensor
  tenga al menos una detección de vegetación nominal/alta en la celda y el día;
  no alcanza con que aparezca en `grid_daily.sensors`. Evita sobrecontar la
  serie diaria.
- **Probabilidad histórica**: es la frecuencia con la que una celda tuvo
  actividad en la misma semana ISO durante los años de referencia. Se estima
  con un prior Beta y se publica con IC 90 % (Wilson), `n` y confiabilidad. No
  es predicción, no incorpora meteorología ni combustible y no es alerta
  oficial. Con `insufficient` se muestra `—`.
- **Calibración declarada**: el holdout 2025 da BSS 0,0286 (< 0,05) y AUC 0,740;
  por eso la etiqueta publicada es `risk-v1-low`. Sirve para triage y
  comparación histórica, no como probabilidad calibrada.
- **La probabilidad del mapa es semanal agregada del rango**, no mensual ni
  diaria. Con NOAA-20/21 no hay campos de riesgo.
- **Sin calibración física** de eficiencia de detección ni de FRP.
- **Sin datos no es sin fuego**: `coverage_daily` es una cobertura **proxy
  regional** (día con al menos una detección por sensor/fuente), no cobertura
  por celda. Una celda nublada puede tener 0 detecciones sin estar libre de
  fuego.
- **CSV**: máximo 50.000 filas; las detecciones aceptan hasta 10.000 días. La
  UI avisa si la descarga se truncó.

Las definiciones completas están en `docs/DECISIONES.md`.

## Documentación

- Demo Docker: `docker/README.md`
- Datos y ETL: `data/README.md`
- Decisiones y definiciones: `docs/DECISIONES.md`
- Capa de riesgo: `informes/INFORME_MODULO_ESTADISTICO_RIESGO.md`
- Scripts, tiempos y validación de riesgo: `backend/db/README.md`
- Plan de riesgo estadístico: `backend/planes/PLAN_RIESGO_ESTADISTICO.md`
- Arquitectura y contrato: `backend/planes/CONTRATO_API.md`
- OpenAPI: `backend/openapi/chronosfire-api.yaml`
- Plan de backend: `backend/planes/PLAN_BACKEND.md`
- Armonización: `backend/planes/PLAN_ARMONIZACION.md`
- Validación: `backend/VALIDACION_6_8.md`
- Frontend local: `frontend/ChronosFire/README-LOCAL.md`
- Funcionalidad del frontend: `frontend/ChronosFire/docs/FUNCIONALIDAD.md`
- Alineación con el challenge: `informes/INFORME_ALINEACION_CHALLENGE.md`
- Brechas y plan de cierre: `informes/INFORME_BRECHAS_Y_PLAN_BACKEND_FRONTEND.md`

La base se usa en modo lectura: el backend no crea ni modifica tablas.
