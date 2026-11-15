# Chronos Fire

Calendario de actividad de fuego para Argentina: MODIS + VIIRS armonizados,
backend Java/Spring Boot y frontend Vue 3.

## Requisitos

- Java 21+
- Maven 3.9+
- Node.js 22.18+ (o 24.12+)
- PostgreSQL 17 con la base `chronos_fire` cargada

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

Frontend:

```powershell
cd frontend\ChronosFire
npm run build
npm run test:api
npm run test:browser   # requiere backend y frontend activos
```

## Documentación

- Arquitectura y contrato: `backend/CONTRATO_API.md`
- OpenAPI: `backend/openapi/chronosfire-api.yaml`
- Plan de backend: `backend/PLAN_BACKEND.md`
- Armonización: `backend/PLAN_ARMONIZACION.md`
- Validación: `backend/VALIDACION_6_8.md`
- Frontend local: `frontend/ChronosFire/README-LOCAL.md`

La base se usa en modo lectura: el backend no crea ni modifica tablas.
