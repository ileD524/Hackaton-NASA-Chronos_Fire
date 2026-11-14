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

- Esquema: `db/ChronosFireDB.sql`
- Máscara industrial: `db/build_industrial_mask.sql`
- Carga con DuckDB: `../etl/load_pg.py`
- Plan del backend: `PLAN_BACKEND.md`
- Plan de armonización: `PLAN_ARMONIZACION.md`

Orden recomendado:

1. Aplicar `ChronosFireDB.sql`.
2. Cargar detecciones con `etl/load_pg.py`.
3. Construir `industrial_cells` con `build_industrial_mask.sql`.
4. Reconstruir agregados con `etl/load_pg.py --aggregates`.

El backend solo consulta la base; la carga sigue siendo responsabilidad del
ETL con DuckDB.
