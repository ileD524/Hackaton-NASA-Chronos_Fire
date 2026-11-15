# ChronosFire · demo local con backend Java

Esta versión del frontend consume el backend Java/Spring Boot sobre PostgreSQL
`chronos_fire`. El servidor Python/DuckDB de `server/` queda como legado y no
se usa.

## Requisitos

- Java 21 o superior.
- Maven 3.9 o superior.
- Node.js 22.18 o superior (o 24.12+).
- PostgreSQL 17 con `chronos_fire` cargada.

## Atajo: backend + frontend juntos

Desde la raíz del repo:

```powershell
Copy-Item backend\.env.example backend\.env
# editar DB_PASSWORD en backend\.env
.\run-app.ps1
```

O usar la task de VS Code **app: dev**.

## 1. Backend manual

```powershell
cd ..\..\backend
$env:DB_PASSWORD = 'postgres'
mvn spring-boot:run
```

Comprobar:

```text
http://127.0.0.1:8080/api/health
http://127.0.0.1:8080/api/meta
http://127.0.0.1:8080/swagger-ui.html
```

## 2. Frontend

```powershell
cd ..\frontend\ChronosFire
npm ci
npm run dev
```

Abrir **http://127.0.0.1:5173**.

Vite redirige `/api` a `http://127.0.0.1:8080`.

## Qué se puede explorar

- Calendario mensual de **celdas con actividad** en una grilla de 0,05°.
- La vista inicial es **Delta del Paraná** para evitar la consulta nacional pesada;
  “Toda la extensión” sigue disponible en el selector de área.
- Detalle diario de celdas y detecciones del mes seleccionado.
- Mapa de celdas con hotspots del mes y desglose por sensor en el popup.
- Comparación con la mediana del mismo mes en otros años completos, con años de
  referencia y confianza.
- Descarga CSV del calendario y de hotspots. La UI avisa si la descarga se
  truncó en el tope de 50.000 filas.
- Alertas NRT, AOIs y validación disponibles en la API.

El calendario usa todos los sensores. El backend conserva el desglose por
sensor en `detectionsBySensor` y en los endpoints de detalle.

## Datos y semántica

- Fuente: NASA FIRMS, PostgreSQL `chronos_fire`.
- Rango: 2000-11-01 a 2026-11-14; el calendario arranca en 2012 por defecto.
- Unidad: celda de 0,05° con `fire_day`; no es cantidad de incendios ni área quemada.
- 2026 es NRT provisional.
- `coverage_daily` está vacía: una semana sin filas no confirma ausencia de fuego.
- No hay calibración física de eficiencia ni de FRP.

## Compilar

```powershell
npm run build
```

## Notas

- El backend Python/DuckDB fue retirado.
- El proxy de Vite apunta al backend Java en el puerto 8080.
- Tests de contrato: `npm run test:api`.
- Tests de navegador: `npm run test:browser` con backend y frontend activos.
