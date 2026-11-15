# ChronosFire · demo local con DSv2

Adaptación de la demo Vue de la rama `Nati` del repositorio [Hackaton-NASA-Chronos_Fire](https://github.com/ileD524/Hackaton-NASA-Chronos_Fire/tree/Nati/ChronosFire). Base: `cd3f3051d5f3c1e0a400abc4c30fc53f225b2dba`.

Para instalarla en otra computadora y conocer todas sus funciones, ver [docs/FUNCIONALIDAD.md](docs/FUNCIONALIDAD.md). Los datos procesados necesarios no están incluidos en este repositorio.

## Abrir

**http://127.0.0.1:8765**

En la computadora donde se preparó la demo, para iniciar el servidor:

```bash
cd /home/matir/Descargas/DSv2/chronosfire-local/ChronosFire
./run-local.sh
```

`Ctrl+C` en su terminal detiene el servidor. El script compila la aplicación y utiliza el Python de `../../armonizacion/.venv`. Escucha exclusivamente en `127.0.0.1`.

## Qué se puede explorar

- Calendario mensual y detalle diario, 2003–2025. MODIS y VIIRS observados, unión de celda-días y VIIRS calibrado a escala MODIS.
- Selección de mes desde la página o el calendario ampliado; ambos sincronizan el mapa y el gráfico diario.
- Filtros de fechas y rectángulo geográfico. Córdoba y Corrientes son cajas de exploración, no recortes administrativos.
- «Usar vista como filtro» consulta el rectángulo visible en el mapa.
- Comparación con la mediana del mismo mes de otros años completos de la selección. Los ceros entran en la mediana; los meses parciales y sin datos no.
- Comparación MODIS/VIIRS/calibrado sobre exactamente los mismos días elegibles, cuando se usa la extensión completa.
- Exportación CSV con los datos diarios de la consulta.

La selección inicial muestra MODIS observado entre 2012 y 2025 y abre agosto de 2020, el mes completo de mayor actividad de ese intervalo. «Ver todo el archivo» incluye 2003–2011.

## De dónde salen los datos

Se usa **DuckDB sobre los Parquet originales**, en modo de consulta; no se importan millones de registros al navegador ni se duplican los datasets dentro del proyecto.

Ruta predeterminada: `/home/matir/Descargas/DSv2/armonizacion`.

Entradas utilizadas:

```text
resultados/vegetacion_nominal_alta.parquet
resultados/calendario_diario.csv
resultados/control_calidad.json
calibracion/serie_calibrada_2012_2025.csv
calibracion/resumen_calibracion.json
```

La vista completa aprovecha los agregados existentes. Los recortes espaciales se recalculan sobre el Parquet. Las consultas recientes se conservan en una caché de memoria acotada; reiniciar el servidor actualiza la lectura si cambian los archivos.

Para usar otra ubicación:

```bash
CHRONOS_DATA_DIR=/ruta/armonizacion CHRONOS_PYTHON=/ruta/venv/bin/python ./run-local.sh
```

En otro entorno, instalar `server/requirements-local.txt` en un entorno Python y las dependencias web con `npm ci`. No se requiere clave FIRMS. Fuentes tipográficas y cartografía se sirven localmente; la aplicación funciona sin conexión después de la instalación. Los enlaces de referencia externos sí requieren conexión.

## Interpretación de los valores

La unidad del calendario es **celda-día de 1 km²**, no cantidad de incendios únicos ni superficie quemada. Una celda activa tres días aporta tres celda-días. Se selecciona vegetación presunta con confianza nominal/alta.

- `MODIS observado` y `VIIRS observado`: celdas diarias con detecciones de ese sensor.
- `Unión`: una celda/día cuenta una sola vez si ambos sensores la detectaron. La incorporación de VIIRS altera la sensibilidad de la serie.
- `VIIRS calibrado`: predicción agregada en escala MODIS mediante los factores mensuales validados. Solo se habilita para la extensión completa de DSv2 y conserva los faltantes del modelo.

El mapa muestra observaciones, incluso cuando se selecciona la calibración. Agrupa visualmente en celdas de proyección de 5 × 5 km y coloca un punto en la media de las coordenadas de sus detecciones. No reconstruye polígonos de incendios. Si hay más de 6.000 agrupaciones, muestra las de mayor conteo e informa cuánto quedó visible; los totales y el calendario siguen completos.

Cero significa cero registros seleccionados, no ausencia confirmada de fuego. Un guion significa que la serie no tiene dato. El asterisco identifica meses parciales. Las sumas mensuales calibradas pueden omitir días por interrupciones; no deben compararse directamente con meses completos. La comparación de barras usa fechas pareadas para evitar ese problema.

La aplicación informa que los intervalos nominales del 90% obtuvieron una cobertura del 82,8% en la prueba 2023–2025. La calibración no se aplica a provincias ni a puntos individuales. El informe científico completo está en `../../armonizacion/calibracion/INFORME.md`.

## Desarrollo y pruebas

```bash
# Terminal 1: API y archivos compilados
../../armonizacion/.venv/bin/python server/api.py

# Terminal 2: Vue con recarga automática y proxy a la API
npm run dev

# Compilación y tipos
npm run build

# Pruebas contra el servidor local activo
../../armonizacion/.venv/bin/python -m unittest discover -s tests -p 'test_*.py' -v
npm run test:browser
```

Las pruebas del navegador usan Chromium de `/usr/bin/chromium`; se puede cambiar con `CHROMIUM_PATH`. Las capturas y el resultado de navegación se guardan en `artifacts/`, fuera de Git.

Verificación realizada: compilación y TypeScript correctos; siete pruebas de API/datos; selección de mes, filtros, restricciones de calibración, exportación, faltantes de VIIRS anteriores a 2012, teclado, modal, vista móvil y contraste de todas las celdas visibles. No se presenta como una auditoría completa de accesibilidad.

## Estructura de esta adaptación

- `server/api.py`: servidor local, validación de parámetros y consultas DuckDB.
- `src/composables/useChronos.ts`: estado compartido, carga y cancelación de consultas.
- `src/composables/useCalendar.ts`: métricas mensuales, medianas y etiquetas.
- `src/components/FireCalendar.vue`: calendario interactivo.
- `src/components/FireMap.vue`: mapa Leaflet con observaciones del archivo.
- `src/components/DailyDetail.vue`: serie diaria, tabla accesible y comparación pareada.
- `docs/MARCA.md`: revisión de identidad visual y cambios.
- `docs/ASSETS.md`: fuentes y licencias de recursos.

Esta es una integración local para evaluar el producto; para un despliegue multiusuario habría que añadir almacenamiento/configuración de servidor, caché compartida y límites de concurrencia acordes al volumen.
