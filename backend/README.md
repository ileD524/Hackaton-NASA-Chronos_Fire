# Chronos Fire - Backend

Carpeta reservada para el backend Java. **La implementación todavía no está hecha**:
acá solo queda la configuración base y la referencia de dónde van los archivos.

## Stack previsto

- Java 21+
- Spring Boot
- PostgreSQL 17 (`chronos_fire`)
- JDBC (`JdbcTemplate` / `NamedParameterJdbcTemplate`)

## Archivos de configuración

```text
backend/
└─ src/main/resources/
   └─ application.properties
```

`application.properties` ya apunta a:

```text
jdbc:postgresql://127.0.0.1:5432/chronos_fire
```

Variables de entorno soportadas:

```text
SERVER_PORT
DB_URL
DB_USER
DB_PASSWORD
DB_POOL_SIZE
CORS_ORIGINS
```

## Dónde agregar el código

Cuando se implemente, la estructura sugerida es:

```text
backend/
└─ src/main/java/com/chronosfire/backend/
   ├─ ChronosFireApplication.java
   ├─ config/
   ├─ controller/
   ├─ dto/
   ├─ repository/
   └─ web/
```

No se agregó `pom.xml` ni código Java: el proyecto se crea cuando se decida
implementar.

## Base de datos y ETL

- Esquema base: `db/ChronosFireDB.sql`
- Corrección del calendario: `db/fix_calendar.sql` (una vez)
- Actividad por sensor: `db/add_grid_weekly_sensor.sql` (una vez)
- Rebuild del calendario: `db/rebuild_calendar.sql` (después de cada carga)
- Máscara industrial: `db/build_industrial_mask.sql`
- Carga con DuckDB: `../etl/load_pg.py`
- Plan del backend: `PLAN_BACKEND.md`
- Plan de armonización: `PLAN_ARMONIZACION.md`
- Scripts de validación §6.8: `db/validacion/`
- Informe de validación §6.8: `VALIDACION_6_8.md`

### Instalación nueva

1. Aplicar `ChronosFireDB.sql`.
2. Aplicar `fix_calendar.sql`.
3. Aplicar `add_grid_weekly_sensor.sql`.
4. Cargar detecciones:
   ```powershell
   python etl\load_pg.py --truncate
   ```
5. Construir máscara y `grid_daily`:
   ```powershell
   python etl\load_pg.py --only __none__ --aggregates
   ```
6. Reconstruir el calendario:
   ```powershell
   & "C:\Program Files\PostgreSQL\17\bin\psql.exe" `
     -h 127.0.0.1 -p 5432 -U postgres -d chronos_fire `
     -f backend\db\rebuild_calendar.sql
   ```

### Base ya existente

Si el esquema y las migraciones ya están aplicados, después de cada carga:

```powershell
python etl\load_pg.py --truncate --aggregates
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" `
  -h 127.0.0.1 -p 5432 -U postgres -d chronos_fire `
  -f backend\db\rebuild_calendar.sql
```

`--aggregates` ya no reconstruye `calendar_weekly`; solo máscara y `grid_daily`.
El calendario se reconstruye con `rebuild_calendar.sql` para aplicar 53 semanas y baseline por sensor.

El backend solo consulta la base; la carga sigue siendo responsabilidad del
ETL con DuckDB.
