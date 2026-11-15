# Funcionalidad e instalación de ChronosFire

Esta demo explora detecciones MODIS y VIIRS de DSv2 entre 2003 y 2025. El navegador consulta un servidor local Python; DuckDB lee los datos procesados desde disco. No requiere clave de FIRMS. Es un prototipo de consulta, no un servicio de alertas ni un producto operativo.

## Instalar en otra computadora

Se necesitan Git, Node.js 22.18 o superior dentro de la rama 22, o 24.12 o superior; Python con soporte para DuckDB 2.0.0; y los archivos procesados de DSv2. Ejemplo para Linux o macOS (en Windows se puede usar WSL):

```bash
git clone --branch mattt https://github.com/ileD524/Hackaton-NASA-Chronos_Fire.git
cd Hackaton-NASA-Chronos_Fire/ChronosFire
python3 -m venv .venv
.venv/bin/python -m pip install -r server/requirements-local.txt
npm ci
export CHRONOS_DATA_DIR="/ruta/absoluta/a/armonizacion"
export CHRONOS_PYTHON="$PWD/.venv/bin/python"
./run-local.sh
```

Abrir <http://127.0.0.1:8765>. El proceso sigue activo hasta pulsar Ctrl+C. Se puede cambiar el puerto con `PORT=8766 ./run-local.sh`. El servidor escucha en la interfaz local; para compartirlo públicamente hace falta configurar un túnel o un despliegue aparte.

### Datos necesarios

Los siguientes archivos deben copiarse desde la carpeta `armonizacion` de la computadora original, conservando la estructura. **No se incluyen en Git** y los ZIP originales por sí solos no permiten ejecutar esta versión: primero hay que generar los resultados armonizados y calibrados con el proceso correspondiente, que tampoco está incluido en este repositorio.

```text
armonizacion/
  resultados/
    vegetacion_nominal_alta.parquet
    calendario_diario.csv
    control_calidad.json
  calibracion/
    serie_calibrada_2012_2025.csv
    resumen_calibracion.json
```

El Parquet ocupa aproximadamente 283 MiB; los demás archivos son pequeños. Se puede compartir esta carpeta por un servicio de archivos y apuntar `CHRONOS_DATA_DIR` a la copia. Conviene compartir el mismo conjunto de archivos y verificar su integridad para obtener los mismos resultados. Un clon sin esos datos no puede iniciar la API. GitHub bloquea archivos individuales superiores a 100 MiB en Git normal; para versionarlos allí habría que evaluar Git LFS y sus cuotas, o usar almacenamiento externo. Las fuentes de letras y el mapa base sí están incluidos.

## Uso de la demo

- **Filtros:** elegir fechas, sensor o serie y, opcionalmente, un rectángulo geográfico. Los accesos a Córdoba y Corrientes son cajas aproximadas, no límites provinciales. El botón de usar la vista toma el rectángulo visible del mapa.
- **Calendario:** cada celda es un mes. Al seleccionar un mes se actualizan el mapa y el detalle diario; el calendario ampliado comparte la selección. El asterisco indica mes parcial y el guion indica dato faltante.
- **Indicadores:** resumen la selección. La comparación histórica usa la mediana del mismo mes en otros años completos dentro del rango, incluyendo ceros y excluyendo meses parciales o sin dato.
- **Mapa:** muestra detecciones observadas, agrupadas visualmente en celdas de 5 × 5 km. Para un mes con más de 6.000 grupos muestra los de mayor conteo y lo indica; las cifras totales siguen usando todos los datos.
- **Detalle:** gráfico y tabla diaria del mes, más comparación de series en días pareados cuando corresponde. La exportación CSV descarga la serie diaria de la selección.
- **Cobertura:** «Ver todo el archivo» incluye 2003–2011; VIIRS comienza en 2012. Un día sin registros no confirma que no hubiera fuego.

## Qué miden las series

La unidad es **celda-día de 1 km²**, con detecciones de vegetación presunta y confianza nominal o alta. Una celda activa tres días suma tres celda-días; no equivale a incendios únicos ni a superficie quemada. MODIS y VIIRS cuentan celdas diarias por sensor; la unión cuenta una sola vez la misma celda y día si ambos la detectan.

VIIRS calibrado es una estimación agregada en escala MODIS mediante factores mensuales, disponible únicamente para la extensión completa del conjunto. No se aplica a provincias, rectángulos ni puntos individuales. Se respetan los días faltantes y se comparan fechas pareadas. En la prueba 2023–2025, los intervalos nominales del 90 % cubrieron 82,8 % de los casos: su incertidumbre está subestimada. El mapa sigue mostrando detecciones observadas al elegir la serie calibrada.

## Desarrollo

`server/api.py` sirve la aplicación compilada y expone salud, metadatos, calendario, mapa y exportación. `src/composables/useChronos.ts` coordina filtros y consultas; los componentes Vue presentan calendario, mapa y detalles. La vista completa utiliza agregados diarios y los filtros espaciales consultan el Parquet. Para desarrollo, iniciar la API y luego `npm run dev`; para comprobar tipos y compilación, `npm run build`. Las pruebas de Python están en `tests/` y la prueba de navegador se ejecuta con `npm run test:browser` y un servidor activo; usa Chromium y admite `CHROMIUM_PATH`.

La revisión visual de la marca está en [MARCA.md](MARCA.md) y la procedencia de recursos en [ASSETS.md](ASSETS.md). Véase [README-LOCAL.md](../README-LOCAL.md) para detalles de interpretación y pruebas.
