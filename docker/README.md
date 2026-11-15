# Demo reproducible con Docker

Este compose levanta PostgreSQL 17 con un dump **acotado y real** de Chronos
Fire, para que un clon limpio pueda abrir la app sin descargar los 7 millones
de detecciones.

## Requisitos

- Docker Desktop con `docker compose`.
- Java 21+, Maven 3.9+ y Node.js 22.18+ para el backend y el frontend.

## Pasos

Desde la raíz del repositorio:

```powershell
docker compose up -d
```

Esperar a que el contenedor quede healthy:

```powershell
docker compose ps
docker compose logs -f db
```

Cuando el dump terminó de cargar, abrir la app en modo demo:

```powershell
.\run-app.ps1 -Demo
```

Abrir:

```text
Frontend: http://127.0.0.1:5173
Backend:  http://127.0.0.1:8080
Swagger:  http://127.0.0.1:8080/swagger-ui.html
```

El flag `-Demo` usa:

```text
DB_URL=jdbc:postgresql://127.0.0.1:5433/chronos_fire_demo
DB_USER=chronos
DB_PASSWORD=chronos
```

También se puede copiar `docker\backend.env.example` a `backend\.env` y correr
`.\run-app.ps1` sin flag, pero eso pisa la configuración local de PostgreSQL.

## Resetear la demo

El dump se ejecuta solo cuando el volumen está vacío. Para reiniciar:

```powershell
docker compose down -v
docker compose up -d
```

## Qué incluye el dump

- Delta del Paraná: calendario y agregados 2013-2025.
- Detecciones 2020 del Delta.
- `grid_weekly_sensor` de las 5 AOIs de validación, 2013-2025.
- Máscara industrial completa (885 celdas).
- `validation_aoi` y `validation_aoi_window`.

La demo es histórica: las detecciones son de 2020, por lo que el panel de
alertas NRT puede aparecer vacío. Las validaciones de eventos y el calendario
sí funcionan.

No incluye el dataset completo. Para eso:

1. Descargar los datos de FIRMS según `data/README.md`.
2. Configurar `CHRONOS_DATA` y `CHRONOS_TMP`.
3. Correr el ETL y las migraciones de `backend/db/`.
4. Apuntar `DB_URL` a la base cargada.

## Archivos

- `docker/demo/chronos_fire_demo.sql.gz`: dump comprimido (~5 MB).
- `docker/backend.env.example`: variables para el backend.
- `docker-compose.yml`: PostgreSQL 17 en `127.0.0.1:5433`.

SHA256 del dump:

```text
2DF40E5F9D8E794C819D189982B35D29EF636AC6AB2F77CA4190CF4FDD4A7D5B
```

## Troubleshooting

- **Puerto 5433 ocupado:** cambiar el mapeo en `docker-compose.yml` y
  `DB_URL` en `docker/backend.env.example`.
- **El backend no conecta:** verificar `docker compose ps` y que
  `chronos-fire-demo-db` esté healthy.
- **No aparecen datos:** el volumen ya existía sin dump; correr
  `docker compose down -v` y volver a levantar.
- **Credenciales:** son solo para la demo local. No usar en producción.
