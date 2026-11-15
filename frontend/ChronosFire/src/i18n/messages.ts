/**
 * Catálogo bilingüe de la interfaz.
 *
 * `es` es la fuente de verdad: las claves y los textos en español definen el
 * tipo `MessageKey`. `en` se declara como `Record<MessageKey, string>` para que
 * TypeScript falle si falta una traducción.
 */
export const es = {
  'app.title': 'VII.MO · Calendario de actividad de fuego',
  'app.description':
    'VII.MO · Calendario de actividad de fuego con celdas MODIS y VIIRS armonizadas (NASA FIRMS).',

  'brand.tagline': 'Calendario de incendios',
  'skip.content': 'Saltar al contenido',

  'nav.main': 'Navegación principal',
  'nav.brand': 'VII.MO · ir al calendario',
  'nav.map': 'Mapa',
  'nav.calendar': 'Calendario',
  'nav.alerts': 'Alertas',
  'nav.validation': 'Validación',
  'nav.methodology': 'Metodología',
  'nav.data': 'Datos',

  'footer.credit': 'Datos: NASA Earthdata FIRMS · MODIS y VIIRS · grilla 0,05°',
  'footer.nasa': 'Proyecto participante de NASA Space Apps {year}',

  'lang.label': 'Idioma de la interfaz',

  'hero.eyebrow': 'Archivo histórico · MODIS + VIIRS · grilla 0,05°',
  'hero.title.1': 'Calendario de',
  'hero.title.2': 'actividad de fuego',
  'hero.sub.pre': 'Explorá ',
  'hero.sub.strong': 'cuándo y dónde se detectó actividad',
  'hero.sub.post': ' y cómo se compara con la historia multianual.',
  'hero.context.label': 'Área de interés',
  'hero.cta': 'Abrir el calendario',
  'hero.map': 'Explorar el mapa histórico',

  'common.retry': 'Reintentar',
  'common.close': 'Cerrar',
  'common.previous': 'Anterior',
  'common.next': 'Siguiente',
  'common.page': 'Página {page} de {total}',
  'common.range': '{start}–{end} de {total}',
  'common.noData': 'Sin datos',
  'format.percent': '{value} %',

  'app.loading': 'Consultando el archivo local…',
  'app.initialTitle': 'Tu archivo de actividad',
  'app.initialError': 'El servidor no pudo cargar los datos.',
  'app.initialLoading': 'Preparando el calendario de detecciones MODIS y VIIRS.',

  'kpi.activity': 'ACTIVIDAD REGISTRADA',
  'kpi.peak': 'MES CON MAYOR VALOR',
  'kpi.complete': 'MESES COMPLETOS',
  'kpi.includesPartial': ' · incluye meses parciales',
  'kpi.peakLabel': '{value} {unit} · mes completo',
  'kpi.peakEmpty': 'Sin meses completos en la selección',
  'kpi.completeLabel': 'en la selección · no acredita cobertura sin nubes · {metric}',

  'unit.cells': 'celdas',
  'unit.hotspots': 'hotspots',
  'unit.metric.cells': 'celdas con actividad',
  'unit.metric.hotspots': 'hotspots detectados',
  'unit.metricRange.cells': 'celdas-mes con actividad',
  'unit.metricRange.hotspots': 'hotspots detectados en el rango',
  'unit.cellsPerDay': 'celdas con actividad / día',
  'unit.hotspotsPerDay': 'hotspots detectados / día',
  'unit.activeCells': 'Celdas con actividad',
  'unit.hotspotsDetected': 'Hotspots detectados',

  'area.all': 'Extensión completa · Argentina',
  'area.delta': 'Delta del Paraná',
  'area.custom': 'Área personalizada · recorte rectangular',

  'series.all': 'Todos los sensores · 0,05°',
  'series.MODIS': 'MODIS observado',
  'series.VIIRS_SNPP': 'VIIRS SNPP',
  'series.VIIRS_NOAA20': 'VIIRS NOAA-20',
  'series.VIIRS_NOAA21': 'VIIRS NOAA-21',

  'sensor.all': 'Todos los sensores (dominado por VIIRS)',
  'sensor.MODIS': 'MODIS',
  'sensor.VIIRS_SNPP': 'VIIRS SNPP',
  'sensor.VIIRS_NOAA20': 'VIIRS NOAA-20',
  'sensor.VIIRS_NOAA21': 'VIIRS NOAA-21',

  'aoi.aria': 'Filtros del calendario',
  'aoi.ariaRegion': 'Filtros y exportación',
  'aoi.kicker': 'EXPLORAR EL ARCHIVO',
  'aoi.title': 'Tu área de interés',
  'aoi.area': 'Área',
  'aoi.sensor': 'Sensor',
  'aoi.west': 'Oeste · longitud',
  'aoi.east': 'Este · longitud',
  'aoi.south': 'Sur · latitud',
  'aoi.north': 'Norte · latitud',
  'aoi.from': 'Desde',
  'aoi.to': 'Hasta',
  'aoi.option.delta': 'Delta del Paraná',
  'aoi.option.all': 'Toda la extensión',
  'aoi.option.cordoba': 'Entorno de Córdoba',
  'aoi.option.corrientes': 'Entorno de Corrientes',
  'aoi.option.custom': 'Rectángulo personalizado',
  'aoi.sensorNote.all': 'No es un promedio: la serie está dominada por VIIRS.',
  'aoi.sensorNote.single':
    'Los meses sin datos de este sensor no se completan con otros sensores.',
  'aoi.sensorNote.scope':
    'El selector afecta calendario, mapa y detalle diario. Las alertas NRT son independientes del sensor.',
  'aoi.bboxNote':
    'Recorte por coordenadas; no representa los límites oficiales de una provincia.',
  'aoi.dataKicker': 'DATOS REALES · NASA FIRMS',
  'aoi.dataModis': 'MODIS Terra/Aqua',
  'aoi.dataViirs': 'VIIRS Suomi-NPP / NOAA-20 / NOAA-21',
  'aoi.dataNote':
    'Vegetación presunta · confianza nominal/alta · días UTC · celdas 0,05°.',
  'aoi.dataDetections': '{count} detecciones en el archivo.',
  'aoi.exportCalendar': '↓ Exportar calendario CSV',
  'aoi.exportHotspots': '↓ Descargar hotspots CSV',
  'aoi.calendarFile': 'chronosfire-calendario-semanal.csv',
  'aoi.hotspotsFile': 'chronosfire-detecciones.csv',
  'aoi.exportHotspotsAria':
    'Descargar hotspots CSV: hasta 50000 filas de vegetación nominal o alta, sin focos industriales',
  'aoi.exportNote':
    'Hasta 50.000 filas. Solo vegetación nominal/alta, sin focos industriales. Usa el área, el rango y el sensor activos.',
  'aoi.zeroNote.cells':
    'Cero registros no confirma ausencia de fuego. La comparación histórica se calcula sobre celdas con actividad.',
  'aoi.zeroNote.hotspots':
    'Cero registros no confirma ausencia de fuego. Los hotspots son conteos de detecciones; la comparación histórica se calcula sobre celdas con actividad.',
  'aoi.validationDate': 'Revisá el orden de las fechas.',
  'aoi.validationBbox': 'Revisá los límites del rectángulo.',
  'aoi.submit': 'Actualizar calendario',
  'aoi.submitting': 'Consultando…',
  'aoi.archive': 'Ver todo el período',
  'aoi.archiveRange': 'Ver todo el período {first}–{last}',

  'csv.limited':
    'Descarga limitada a {rows} filas. Acotá el área o el rango para obtener el resto.',
  'csv.complete': 'Descarga completa: {rows} filas.',
  'csv.completeNoRows': 'Descarga completa.',

  'map.kicker': 'ACTIVIDAD DEL MES',
  'map.title.empty': 'Elegí un mes',
  'map.probabilityNoSupport': 'Probabilidad histórica: sin respaldo',
  'map.ci': ' · IC 90 % {low}–{high} %',
  'map.probability': 'Probabilidad histórica: {value} % (n={n}{interval})',
  'map.popupHotspots': '{value} hotspots',
  'map.popupDays': '{value} días con actividad',
  'map.popupNoHotspots': 'Sin hotspots',
  'map.popupCell': 'Celda {id} · 0,05°',
  'map.ramp.riskCaption': 'Probabilidad histórica · cortes fijos',
  'map.ramp.sequentialCaption': 'Intensidad de hotspots · quintiles de lo mostrado',
  'map.ramp.riskRange': '0 % → ≥ 20 %',
  'map.ramp.sequentialRange': 'bajo → alto',
  'map.ramp.riskAria': 'Escala de probabilidad histórica con cortes {cuts}',
  'map.ramp.sequentialAria': 'Escala de intensidad de hotspots de menor a mayor',
  'map.attribution': 'Cartografía: Natural Earth · Datos: NASA FIRMS',
  'map.cartographyError': 'No se pudo cargar la cartografía local.',
  'map.expand': 'Ampliar mapa',
  'map.chooseOther': 'Elegir otro mes ↗',
  'map.close': 'Cerrar',
  'map.toolbarTitle': 'Las detecciones de distintos sensores no se suman',
  'map.cellsAndHotspots':
    '{cells} celdas con actividad · {hotspots} hotspots (todas las fuentes)',
  'map.loadingCells': 'Cargando celdas…',
  'map.noMonth': 'Sin mes seleccionado',
  'map.colorBy': 'Colorear celdas por',
  'map.hotspots': 'Hotspots',
  'map.probabilityButton': 'Probabilidad',
  'map.series': 'Serie: {sensor}',
  'map.center': 'Centrar área',
  'map.useView': 'Usar vista como filtro',
  'map.canvasAria': 'Mapa interactivo de celdas con actividad',
  'map.querying': 'Consultando el mes…',
  'map.error': 'Error al cargar el mapa.',
  'map.noCells': 'No hay celdas con actividad en este mes y área.',
  'map.legend.noSupport': 'Sin respaldo',
  'map.legend.size': 'Tamaño: días con actividad',
  'map.howto': 'Cómo leer este mapa',
  'map.howtoText':
    'Grilla de 0,05° (~5 km). El color puede mostrar la intensidad de hotspots (quintiles de las celdas mostradas) o la probabilidad histórica de la celda en el rango (cortes fijos de 0–1, 1–3, 3–8, 8–20 y ≥20 %); el tamaño indica los días con actividad. Doble codificación: el color resume la intensidad y el tamaño los días con actividad. El gris marca celdas sin respaldo histórico. El mapa muestra hotspots; el calendario puede alternar entre celdas con actividad y hotspots. Los hotspots de distintos sensores no se suman como promedio.',
  'map.howtoShown':
    'Se muestran hasta 5.000 celdas ordenadas por días con actividad; los totales corresponden a lo mostrado.',
  'map.howtoShownPartial':
    ' Vista parcial: acotá el área o el mes para ver el resto.',

  'calendar.kicker': 'LA ACTIVIDAD A TRAVÉS DEL TIEMPO',
  'calendar.title': 'Calendario de actividad',
  'calendar.note.cells':
    'Celdas de 0,05° (~5 km) con actividad por mes. No mide área quemada ni cantidad de incendios únicos.',
  'calendar.note.hotspots':
    'Hotspots detectados por mes: detecciones de vegetación nominal/alta, sin focos industriales. Varios hotspots pueden caer en una misma celda y no son incendios únicos.',
  'calendar.metricAria': 'Métrica del calendario',
  'calendar.metric.cells': 'Celdas con actividad',
  'calendar.metric.hotspots': 'Hotspots detectados',
  'calendar.scaleAria': 'Escala del calendario',
  'calendar.ramp.sequential': 'Actividad mensual',
  'calendar.ramp.anomaly': 'Diferencia histórica',
  'calendar.ramp.hotspots': 'Hotspots detectados',
  'calendar.anomalyTitle':
    'Desviación respecto de la mediana de otros años completos',
  'calendar.anomalyNote':
    'Cortes fijos en ±15% y ±50%. ▼ marca los meses por debajo de −50%, ▲ los que superan +50%. Los meses parciales o sin referencia no se comparan y quedan en el tramo central.',
  'calendar.sequentialTitle': '{metric} · quintiles de la selección',
  'calendar.sequentialNote':
    'Cinco quintiles de los meses con valor en la selección; el número de cada celda es el valor real.',
  'calendar.scrollAria': 'Tabla desplazable del calendario',
  'calendar.caption':
    '{metric} por mes y año; seleccioná un mes para actualizar el mapa y el detalle diario.',
  'calendar.yearTotal': 'Año / total',
  'calendar.totalTitle': '{value} {metric} en {year}',
  'calendar.noData': 'Sin datos',
  'calendar.observed': 'Mes con datos',
  'calendar.partialSuffix': ' · período parcial*',
  'calendar.exportSvg': 'Descargar el calendario en SVG',
  'calendar.exportPng': 'Descargar el calendario en PNG',
  'calendar.generatingPng': 'Generando PNG…',
  'calendar.exportNote':
    'El SVG es vectorial y editable; el PNG usa la misma vista a 2×. Ambos incluyen valores, colores, leyenda y nota metodológica.',
  'calendar.footPartial':
    '* Mes recortado por el rango consultado. — Sin datos para esta serie. El cero se conserva como cero {unit}.',
  'calendar.footHotspots':
    'La comparación histórica y la rampa de anomalía se calculan sobre celdas con actividad. Los hotspots se muestran como conteo de detecciones.',
  'calendar.footAbbrev':
    'Los valores de más de cuatro cifras se abrevian en la grilla; el valor completo está en el tooltip y en la etiqueta accesible.',
  'calendar.svgTitle': 'Calendario de actividad de fuego',
  'calendar.svgMetric': '{metric} por mes',
  'calendar.svgNote.cells':
    'Celdas de 0,05° (~5 km) con actividad por mes. No mide área quemada ni cantidad de incendios únicos.',
  'calendar.svgNote.hotspots':
    'Hotspots detectados por mes: detecciones de vegetación nominal/alta, sin focos industriales. Varios hotspots pueden caer en una misma celda y no son incendios únicos.',
  'calendar.svgNote.comparisonCells':
    'La comparación histórica se calcula sobre celdas con actividad.',
  'calendar.svgNote.zero':
    'El cero se conserva como cero; el guion indica que no hay datos para esta serie.',
  'calendar.svgNote.partial': '* Mes recortado por el rango consultado.',
  'calendar.svgNote.sensors':
    '«Todos los sensores» es la unión de fuentes, no un promedio: la serie está dominada por VIIRS y las detecciones de distintos sensores no se suman.',
  'calendar.svgNote.anomaly':
    '▼ marca los meses por debajo de −50% y ▲ los que superan +50% respecto de la mediana de otros años completos.',
  'calendar.svgFooter':
    'Chronos Fire · NASA FIRMS · MODIS + VIIRS · Generado el {date}',
  'calendar.filenamePrefix': 'calendario',
  'calendar.export.svgDone': 'SVG descargado: {file}',
  'calendar.export.pngDone': 'PNG descargado: {file}',
  'calendar.export.pngError': 'No se pudo generar el PNG.',

  'legend.sequentialTitle.cells': 'Celdas con actividad · quintiles de la selección',
  'legend.sequentialTitle.hotspots':
    'Hotspots detectados · quintiles de la selección',
  'legend.anomalyTitle':
    'Desviación respecto de la mediana de otros años completos',
  'legend.anomalyNote':
    'Cortes fijos en ±15% y ±50%. Rampa púrpura–naranja (PuOr) apta para daltonismo; ▼ y ▲ no dependen del color. Los meses parciales o sin referencia no se comparan y quedan en el tramo central.',
  'legend.sequentialNote':
    'Cinco quintiles de los meses con valor en la selección: cada tramo agrupa un 20% de los meses y la leyenda muestra sus cortes exactos. Rampa {name}, perceptualmente uniforme y apta para daltonismo. El número de cada celda es siempre el valor real de {metric}.',

  'modal.title': 'Calendario de actividad de fuego',
  'modal.hint':
    'Elegí un mes para explorar sus celdas con actividad y el detalle diario.',
  'modal.closeAria': 'Cerrar el calendario',

  'daily.kicker': 'ACERCARSE AL DETALLE',
  'daily.title': 'Detalle diario',
  'daily.unit.cells': 'celdas con actividad / día',
  'daily.unit.hotspots': 'hotspots detectados / día',
  'daily.loading': 'Cargando detalle diario…',
  'daily.error': 'No se pudo cargar el detalle diario.',
  'daily.empty': 'Sin datos diarios para este período.',
  'daily.noActivity.cells': 'Sin celdas con actividad registradas en este período.',
  'daily.noActivity.hotspots': 'Sin hotspots registrados en este período.',
  'daily.ariaChart': '{metric} por día en {month}',
  'daily.ariaSelected': 'el mes seleccionado',
  'daily.details': 'Ver valores diarios accesibles',
  'daily.caption.cells':
    'Serie diaria de celdas con actividad; los hotspots por sensor no se suman entre sí como promedio.',
  'daily.caption.hotspots':
    'Serie diaria de hotspots detectados; los hotspots por sensor no se suman entre sí como promedio.',
  'daily.date': 'Fecha UTC',
  'daily.cells': 'Celdas con actividad',
  'daily.modis': 'Hotspots MODIS',
  'daily.viirs': 'Hotspots VIIRS',
  'daily.total': 'Total hotspots',

  'alerts.kicker': 'ALERTA TEMPRANA · NRT',
  'alerts.title': 'Alertas recientes',
  'alerts.refresh': 'Actualizar',
  'alerts.refreshing': 'Actualizando…',
  'alerts.updated.lessMinute': 'Actualizado hace menos de un minuto',
  'alerts.updated.minutes': 'Actualizado hace {n} min',
  'alerts.updated.hours': 'Actualizado hace {n} h',
  'alerts.summary':
    'Últimos {days} días · datos provisionales · última detección {time}',
  'alerts.helpSummary': '¿Qué significan los niveles y la frecuencia?',
  'alerts.help.warning':
    'Prioridad alta: recurrencia ≥ 50 % o actividad muy inusual (percentil ≥ 90 %) con base histórica o al menos 10 detecciones.',
  'alerts.help.watch':
    'Seguimiento: recurrencia ≥ 25 % o actividad inusual (percentil ≥ 75 %) con base histórica o al menos 3 detecciones.',
  'alerts.help.info':
    'Sin prioridad: actividad dentro de lo esperado o sin respaldo histórico suficiente.',
  'alerts.help.triage':
    'Son niveles de triage para priorizar la revisión; no son una alerta oficial ni una predicción.',
  'alerts.help.formula':
    'La frecuencia histórica es la frecuencia con la que esta celda de 0,05° tuvo actividad en esta misma semana del año durante los años de referencia (por ejemplo, 13 años de SNPP). Se estima con p = (k + α) / (n + α + β), donde k son los años con fuego y n los años con cobertura.',
  'alerts.help.notPrediction':
    'No es una predicción ni una alerta oficial: la cobertura es un proxy regional y la probabilidad es una cota inferior. Con 13 años el intervalo es ancho a propósito y se muestra junto al valor.',
  'alerts.loading': 'Consultando alertas…',
  'alerts.empty': 'Sin alertas NRT en el área y período.',
  'alerts.detections': 'Detecciones',
  'alerts.power': 'Potencia',
  'alerts.frequency': 'Frecuencia histórica',
  'alerts.seeInterval': 'Ver intervalo y años de referencia. {bar}',
  'alerts.ref.none': 'sin años de referencia',
  'alerts.noInterval': 'sin intervalo',
  'alerts.ref.one': '1 año de referencia',
  'alerts.ref.many': '{n} años de referencia',
  'alerts.percentile': 'Percentil {n}',
  'alerts.percentileTitle':
    'Actividad reciente: percentil {n} de los años de referencia',
  'alerts.tip.ci': 'IC 90 %',
  'alerts.tip.reference': 'Referencia',
  'alerts.tip.noCoverage': 'sin años con cobertura',
  'alerts.tip.coverage': '{n} años con cobertura',
  'alerts.tip.activity':
    'Actividad reciente: percentil {n}; el {n} % de los años de referencia tuvo menos detecciones en esta semana.',
  'alerts.tip.disclaimer':
    'Frecuencia histórica estimada: no es predicción ni riesgo oficial.',
  'alerts.barLabel':
    'Intervalo de confianza 90 %: {interval}. Frecuencia estimada: {value}.',
  'alerts.sensors': 'Sensores',
  'alerts.pagerAria': 'Paginación de alertas',
  'alerts.prevAria': 'Página anterior de alertas',
  'alerts.nextAria': 'Siguiente página de alertas',
  'alerts.aoiAnomaly': 'Anomalía del AOI: {value}×.',

  'validation.kicker': 'VALIDACIÓN CON EVENTOS',
  'validation.title': 'Eventos conocidos',
  'validation.intro':
    'Compara cada evento real con lo que suele pasar en esa misma semana. Si la anomalía supera 1×, los datos reproducen el pico.',
  'validation.howSummary': 'Cómo se calcula',
  'validation.howText':
    'Baseline: mediana de la misma semana ISO en los años de referencia del sensor, sin contar el año evaluado (leave-one-out). La anomalía indica cuántas veces ese baseline se observó en la semana del evento.',
  'validation.verdictAria': 'Qué significa cada veredicto',
  'validation.verdict.pass': 'Pasa',
  'validation.verdict.fail': 'No pasa',
  'validation.verdict.insufficient': 'Sin datos',
  'validation.help.pass':
    'El evento se reproduce: anomalía mayor a 1× con baseline confiable.',
  'validation.help.fail':
    'La anomalía es 1× o menos: los datos no muestran el pico esperado.',
  'validation.help.insufficient':
    'No hay con qué comparar: faltan años de referencia o hay muy pocas detecciones.',
  'validation.loading': 'Calculando validación…',
  'validation.error': 'No se pudo cargar la validación.',
  'validation.week': 'Semana {week} · {year}',
  'validation.verdictTitle': 'Veredicto técnico: {verdict}',
  'validation.noAnomaly': 'sin anomalía comparable',
  'validation.timesBaseline': 'veces el baseline',
  'validation.observed': 'Observado',
  'validation.baseline': 'Baseline',
  'validation.pagerAria': 'Paginación de eventos',
  'validation.prevAria': 'Página anterior de eventos',
  'validation.nextAria': 'Siguiente página de eventos',

  'method.kicker': 'DE LA OBSERVACIÓN A LA COMPARACIÓN',
  'method.title': 'Qué estás viendo',
  'method.coverage':
    '{coverage}. Vegetación presunta con confianza nominal o alta.',
  'method.step1.strong': 'Una grilla común.',
  'method.step1.body':
    'Detecciones asignadas por su centro a celdas de 0,05° (~5 km).',
  'method.step2.strong': 'Un día común.',
  'method.step2.body':
    'Agregación diaria en UTC; una celda con actividad en un día cuenta una vez.',
  'method.step3.strong': 'Una serie comparable.',
  'method.step3.body':
    'El calendario usa celdas con actividad, no una suma de detecciones entre sensores.',
  'method.definition.strong': 'Celda con actividad ≠ hotspot ≠ incendio.',
  'method.definition.body':
    'Una celda puede contener varios hotspots; un hotspot es una detección satelital y un incendio único requeriría agrupar eventos. La comparación histórica se calcula sobre celdas con actividad; los hotspots se muestran como conteo de detecciones.',
  'method.riskTitle': 'Probabilidad histórica por celda-semana',
  'method.riskFormula':
    'Para cada celda de 0,05° y semana ISO se estima con qué frecuencia hubo actividad en los años de referencia: p = (k + α) / (n + α + β), donde k son los años con fuego, n los años con cobertura y α/β un prior semanal débil (κ = 8). El intervalo es Wilson al 90 % sobre k/n.',
  'method.riskCoverage':
    'n usa una cobertura proxy: un día cuenta como cubierto si el sensor registró al menos una detección en la región. No prueba ausencia de nubes, así que la probabilidad es una cota inferior. Con 13 años el intervalo es ancho a propósito y se muestra.',
  'method.riskLevels':
    'El percentil de actividad compara las detecciones de los últimos 7 días con los años de referencia de esa celda-semana. Los niveles priorizan el triage: warning con p ≥ 50 % o percentil ≥ 90 % con base histórica (p ≥ 10 %) o al menos 10 detecciones; watch con p ≥ 25 % o percentil ≥ 75 % con base o al menos 3 detecciones; info en el resto. Son umbrales provisionales.',
  'method.disclaimer':
    'No es una predicción, no incorpora meteorología ni combustible, y no es una alerta oficial.',
  'method.calibration': 'Calibración medida (holdout {year}):',
  'method.calibrationLine':
    'Brier {brier} · BSS {bss} · AUC {auc} · {cells} celdas-semana.',
  'method.calibrationLow':
    'El BSS queda por debajo del umbral {threshold}: se publica como {version} y no se promete calibración.',
  'method.calibrationStability':
    'Ventana corta 2019–2024: BSS {bss}.',
  'method.audienceTitle': 'Para quién',
  'method.audience.emergency.strong': 'Respuesta a emergencias:',
  'method.audience.emergency.body':
    'detectar semanas críticas y comparar el mes actual con su historia.',
  'method.audience.science.strong': 'Científicos:',
  'method.audience.science.body':
    'explorar patrones estacionales y anomalías multianuales.',
  'method.audience.land.strong': 'Gestión del territorio:',
  'method.audience.land.body':
    'priorizar el monitoreo en áreas con actividad recurrente.',
  'method.useCase':
    'Caso de validación semanal: Delta del Paraná, semana 40 de 2020: 6,6× la mediana (66 celdas con actividad frente a 10 de referencia).',
  'method.note':
    'Herramienta descriptiva: los niveles info/watch/warning priorizan el triage; no son predicción ni alerta oficial.',
  'method.limits': 'Límites de la comparación',
  'method.limits1':
    'La confianza se homologa a baja, nominal y alta. El baseline usa la mediana multianual por AOI y la era de cada sensor.',
  'method.limits2':
    'No hay calibración física de eficiencia de detección ni de FRP. La cobertura se aproxima con los días con detecciones (coverage_daily); no descuenta nubosidad ni overpass. La ausencia de registros no confirma ausencia de fuego.',
  'method.provisional':
    '{period} es NRT provisional y queda fuera del baseline. ',
  'method.limits3': 'Una celda de 0,05° no representa superficie quemada.',
  'method.firms': 'Conocer NASA FIRMS ↗',

  'comparison.kicker': 'PONER EL MES EN CONTEXTO',
  'comparison.title': 'Frente a su historia',
  'comparison.monthLine': '{month} {year} · {series}',
  'comparison.deltaNone':
    'No hay una comparación válida para este período parcial o sin referencia.',
  'comparison.deltaNote':
    'Diferencia respecto de la mediana del mismo mes en otros años completos de la selección.',
  'comparison.refYears':
    ' · {n} años de referencia · confianza {confidence}',
  'comparison.confidence.high': 'alta',
  'comparison.confidence.low': 'baja',
  'comparison.confidence.insufficient': 'insuficiente',
  'comparison.selectedMonth': 'Mes seleccionado',
  'comparison.median': 'Mediana de referencia',
  'comparison.historicalComparison': 'Comparación histórica',
  'comparison.cellsUnit': 'celdas con actividad',
  'comparison.hotspotsMonth':
    'Hotspots detectados en el mes seleccionado. La comparación histórica se calcula sobre celdas con actividad; los hotspots son conteos de detecciones y dependen de la constelación de sensores.',
  'comparison.hotspotsSelected': '{value} hotspots detectados',
  'comparison.notAvailable': 'No disponible para hotspots',
  'comparison.allSensors':
    'La serie «todos los sensores» no es neutral: está dominada por VIIRS.',
  'comparison.selectMonth': 'Seleccioná un mes del calendario.',
  'comparison.foot.cells':
    'Comparación descriptiva de esta serie. No es una predicción ni un nivel de riesgo.',
  'comparison.foot.hotspots':
    'Los hotspots se muestran como conteo. No se comparan con una mediana multianual para no confundir detecciones con incendios.',

  'cell.noData': 'sin datos para esta serie',
  'cell.partial': ', período parcial',
  'cell.delta': ', {sign}{pct}% frente a la mediana de otros años completos',

  'band.low-extreme': '≤ −50%',
  'band.low': '−50 a −15%',
  'band.neutral': '±15%',
  'band.high': '+15 a +50%',
  'band.high-extreme': '≥ +50%',

  'risk.cuts': '0–1 %, 1–3 %, 3–8 %, 8–20 %, ≥ 20 %',

  'color.ramp.volume': 'Volumen observado',
  'color.ramp.probability': 'Probabilidad histórica',
  'color.ramp.anomaly': 'Diferencia frente a la mediana',

  'error.fetch': 'No se pudo cargar la consulta.',
  'error.range': 'El servidor no informó el rango de fechas.',
  'error.query': 'Error al consultar los datos.',
  'error.daily': 'No se pudo cargar el detalle diario.',
  'error.server': 'Servidor no disponible.',
  'error.alerts': 'No se pudieron cargar las alertas.',
  'error.validation': 'No se pudo cargar la validación.',
  'error.csv': 'No se pudo verificar el CSV.',
  'error.csvStatus': 'No se pudo verificar el CSV (HTTP {status}).',
  'error.pngInvalid': 'El SVG no tiene dimensiones válidas para generar el PNG.',
  'error.pngRaster': 'No se pudo rasterizar el SVG del calendario.',
  'error.pngCanvas': 'El navegador no permite generar el PNG.',
  'error.pngGenerate': 'No se pudo generar el PNG.',
} as const

export type MessageKey = keyof typeof es

export const en: Record<MessageKey, string> = {
  'app.title': 'VII.MO · Fire activity calendar',
  'app.description':
    'VII.MO · Fire activity calendar with harmonized MODIS and VIIRS cells (NASA FIRMS).',

  'brand.tagline': 'Fire calendar',
  'skip.content': 'Skip to content',

  'nav.main': 'Main navigation',
  'nav.brand': 'VII.MO · go to the calendar',
  'nav.map': 'Map',
  'nav.calendar': 'Calendar',
  'nav.alerts': 'Alerts',
  'nav.validation': 'Validation',
  'nav.methodology': 'Methodology',
  'nav.data': 'Data',

  'footer.credit': 'Data: NASA Earthdata FIRMS · MODIS and VIIRS · 0.05° grid',
  'footer.nasa': 'NASA Space Apps {year} participating project',

  'lang.label': 'Interface language',

  'hero.eyebrow': 'Historical archive · MODIS + VIIRS · 0.05° grid',
  'hero.title.1': 'Fire activity',
  'hero.title.2': 'calendar',
  'hero.sub.pre': 'Explore ',
  'hero.sub.strong': 'when and where activity was detected',
  'hero.sub.post': ' and how it compares with the multi-year history.',
  'hero.context.label': 'Area of interest',
  'hero.cta': 'Open the calendar',
  'hero.map': 'Explore the historical map',

  'common.retry': 'Retry',
  'common.close': 'Close',
  'common.previous': 'Previous',
  'common.next': 'Next',
  'common.page': 'Page {page} of {total}',
  'common.range': '{start}–{end} of {total}',
  'common.noData': 'No data',
  'format.percent': '{value}%',

  'app.loading': 'Querying the local archive…',
  'app.initialTitle': 'Your activity archive',
  'app.initialError': 'The server could not load the data.',
  'app.initialLoading':
    'Preparing the calendar of MODIS and VIIRS detections.',

  'kpi.activity': 'RECORDED ACTIVITY',
  'kpi.peak': 'MONTH WITH HIGHEST VALUE',
  'kpi.complete': 'COMPLETE MONTHS',
  'kpi.includesPartial': ' · includes partial months',
  'kpi.peakLabel': '{value} {unit} · complete month',
  'kpi.peakEmpty': 'No complete months in the selection',
  'kpi.completeLabel':
    'in the selection · does not guarantee cloud-free coverage · {metric}',

  'unit.cells': 'cells',
  'unit.hotspots': 'hotspots',
  'unit.metric.cells': 'active cells',
  'unit.metric.hotspots': 'detected hotspots',
  'unit.metricRange.cells': 'active cell-months',
  'unit.metricRange.hotspots': 'hotspots detected in range',
  'unit.cellsPerDay': 'active cells per day',
  'unit.hotspotsPerDay': 'detected hotspots per day',
  'unit.activeCells': 'Active cells',
  'unit.hotspotsDetected': 'Detected hotspots',

  'area.all': 'Full extent · Argentina',
  'area.delta': 'Paraná Delta',
  'area.custom': 'Custom area · rectangular clip',

  'series.all': 'All sensors · 0.05°',
  'series.MODIS': 'MODIS observed',
  'series.VIIRS_SNPP': 'VIIRS SNPP',
  'series.VIIRS_NOAA20': 'VIIRS NOAA-20',
  'series.VIIRS_NOAA21': 'VIIRS NOAA-21',

  'sensor.all': 'All sensors (VIIRS-dominated)',
  'sensor.MODIS': 'MODIS',
  'sensor.VIIRS_SNPP': 'VIIRS SNPP',
  'sensor.VIIRS_NOAA20': 'VIIRS NOAA-20',
  'sensor.VIIRS_NOAA21': 'VIIRS NOAA-21',

  'aoi.aria': 'Calendar filters',
  'aoi.ariaRegion': 'Filters and export',
  'aoi.kicker': 'EXPLORE THE ARCHIVE',
  'aoi.title': 'Your area of interest',
  'aoi.area': 'Area',
  'aoi.sensor': 'Sensor',
  'aoi.west': 'West · longitude',
  'aoi.east': 'East · longitude',
  'aoi.south': 'South · latitude',
  'aoi.north': 'North · latitude',
  'aoi.from': 'From',
  'aoi.to': 'To',
  'aoi.option.delta': 'Paraná Delta',
  'aoi.option.all': 'Entire extent',
  'aoi.option.cordoba': 'Córdoba area',
  'aoi.option.corrientes': 'Corrientes area',
  'aoi.option.custom': 'Custom rectangle',
  'aoi.sensorNote.all': 'This is not an average: the series is dominated by VIIRS.',
  'aoi.sensorNote.single':
    'Months without data for this sensor are not filled in from other sensors.',
  'aoi.sensorNote.scope':
    'The selector affects the calendar, map and daily detail. NRT alerts are independent of the sensor.',
  'aoi.bboxNote':
    'Coordinate clipping; it does not represent official province boundaries.',
  'aoi.dataKicker': 'REAL DATA · NASA FIRMS',
  'aoi.dataModis': 'MODIS Terra/Aqua',
  'aoi.dataViirs': 'VIIRS Suomi-NPP / NOAA-20 / NOAA-21',
  'aoi.dataNote':
    'Presumed vegetation · nominal/high confidence · UTC days · 0.05° cells.',
  'aoi.dataDetections': '{count} detections in the archive.',
  'aoi.exportCalendar': '↓ Export calendar CSV',
  'aoi.exportHotspots': '↓ Download hotspots CSV',
  'aoi.calendarFile': 'chronosfire-calendar-weekly.csv',
  'aoi.hotspotsFile': 'chronosfire-detections.csv',
  'aoi.exportHotspotsAria':
    'Download hotspots CSV: up to 50,000 rows of nominal or high confidence vegetation, excluding industrial sources',
  'aoi.exportNote':
    'Up to 50,000 rows. Nominal/high-confidence vegetation only, excluding industrial sources. Uses the active area, range and sensor.',
  'aoi.zeroNote.cells':
    'Zero records do not confirm the absence of fire. The historical comparison is computed over cells with activity.',
  'aoi.zeroNote.hotspots':
    'Zero records do not confirm the absence of fire. Hotspots are detection counts; the historical comparison is computed over cells with activity.',
  'aoi.validationDate': 'Check the date order.',
  'aoi.validationBbox': 'Check the rectangle bounds.',
  'aoi.submit': 'Update calendar',
  'aoi.submitting': 'Querying…',
  'aoi.archive': 'View the full period',
  'aoi.archiveRange': 'View the full period {first}–{last}',

  'csv.limited':
    'Download limited to {rows} rows. Narrow the area or range to get the rest.',
  'csv.complete': 'Full download: {rows} rows.',
  'csv.completeNoRows': 'Full download.',

  'map.kicker': 'MONTHLY ACTIVITY',
  'map.title.empty': 'Choose a month',
  'map.probabilityNoSupport': 'Historical probability: no support',
  'map.ci': ' · 90% CI {low}–{high}%',
  'map.probability': 'Historical probability: {value}% (n={n}{interval})',
  'map.popupHotspots': '{value} hotspots',
  'map.popupDays': '{value} days with activity',
  'map.popupNoHotspots': 'No hotspots',
  'map.popupCell': 'Cell {id} · 0.05°',
  'map.ramp.riskCaption': 'Historical probability · fixed cuts',
  'map.ramp.sequentialCaption': 'Hotspot intensity · quintiles of what is shown',
  'map.ramp.riskRange': '0% → ≥ 20%',
  'map.ramp.sequentialRange': 'low → high',
  'map.ramp.riskAria': 'Historical probability scale with cuts {cuts}',
  'map.ramp.sequentialAria': 'Hotspot intensity scale from low to high',
  'map.attribution': 'Basemap: Natural Earth · Data: NASA FIRMS',
  'map.cartographyError': 'The local basemap could not be loaded.',
  'map.expand': 'Expand map',
  'map.chooseOther': 'Choose another month ↗',
  'map.close': 'Close',
  'map.toolbarTitle': 'Detections from different sensors are not added together',
  'map.cellsAndHotspots':
    '{cells} active cells · {hotspots} hotspots (all sources)',
  'map.loadingCells': 'Loading cells…',
  'map.noMonth': 'No month selected',
  'map.colorBy': 'Color cells by',
  'map.hotspots': 'Hotspots',
  'map.probabilityButton': 'Probability',
  'map.series': 'Series: {sensor}',
  'map.center': 'Center area',
  'map.useView': 'Use view as filter',
  'map.canvasAria': 'Interactive map of active cells',
  'map.querying': 'Querying the month…',
  'map.error': 'Error loading the map.',
  'map.noCells': 'No active cells in this month and area.',
  'map.legend.noSupport': 'No support',
  'map.legend.size': 'Size: days with activity',
  'map.howto': 'How to read this map',
  'map.howtoText':
    '0.05° (~5 km) grid. Color can show hotspot intensity (quintiles of the displayed cells) or the historical probability of the cell in the range (fixed cuts at 0–1, 1–3, 3–8, 8–20 and ≥20%); size indicates days with activity. Double encoding: color summarizes intensity and size shows days with activity. Gray marks cells without historical support. The map shows hotspots; the calendar can toggle between active cells and hotspots. Hotspots from different sensors are not averaged together.',
  'map.howtoShown':
    'Up to 5,000 cells are shown, sorted by days with activity; totals refer to what is shown.',
  'map.howtoShownPartial':
    ' Partial view: narrow the area or month to see the rest.',

  'calendar.kicker': 'ACTIVITY OVER TIME',
  'calendar.title': 'Activity calendar',
  'calendar.note.cells':
    '0.05° (~5 km) cells with activity per month. It does not measure burned area or the number of unique fires.',
  'calendar.note.hotspots':
    'Detected hotspots per month: nominal/high-confidence vegetation detections, excluding industrial sources. Several hotspots may fall in the same cell and are not unique fires.',
  'calendar.metricAria': 'Calendar metric',
  'calendar.metric.cells': 'Active cells',
  'calendar.metric.hotspots': 'Detected hotspots',
  'calendar.scaleAria': 'Calendar scale',
  'calendar.ramp.sequential': 'Monthly activity',
  'calendar.ramp.anomaly': 'Historical difference',
  'calendar.ramp.hotspots': 'Detected hotspots',
  'calendar.anomalyTitle':
    'Deviation from the median of other complete years',
  'calendar.anomalyNote':
    'Fixed cuts at ±15% and ±50%. ▼ marks months below −50%, ▲ those above +50%. Partial or unreferenced months are not compared and fall in the central range.',
  'calendar.sequentialTitle': '{metric} · quintiles of the selection',
  'calendar.sequentialNote':
    "Five quintiles of the months with a value in the selection; each cell's number is the actual value.",
  'calendar.scrollAria': 'Scrollable calendar table',
  'calendar.caption':
    '{metric} by month and year; select a month to update the map and daily detail.',
  'calendar.yearTotal': 'Year / total',
  'calendar.totalTitle': '{value} {metric} in {year}',
  'calendar.noData': 'No data',
  'calendar.observed': 'Month with data',
  'calendar.partialSuffix': ' · partial period*',
  'calendar.exportSvg': 'Download the calendar as SVG',
  'calendar.exportPng': 'Download the calendar as PNG',
  'calendar.generatingPng': 'Generating PNG…',
  'calendar.exportNote':
    'The SVG is vector and editable; the PNG uses the same view at 2×. Both include values, colors, legend and methodology note.',
  'calendar.footPartial':
    '* Month clipped by the queried range. — No data for this series. Zero is kept as zero {unit}.',
  'calendar.footHotspots':
    'Historical comparison and the anomaly ramp are computed over cells with activity. Hotspots are shown as detection counts.',
  'calendar.footAbbrev':
    'Values with more than four digits are abbreviated in the grid; the full value is in the tooltip and accessible label.',
  'calendar.svgTitle': 'Fire activity calendar',
  'calendar.svgMetric': '{metric} per month',
  'calendar.svgNote.cells':
    '0.05° (~5 km) cells with activity per month. It does not measure burned area or the number of unique fires.',
  'calendar.svgNote.hotspots':
    'Detected hotspots per month: nominal/high-confidence vegetation detections, excluding industrial sources. Several hotspots may fall in the same cell and are not unique fires.',
  'calendar.svgNote.comparisonCells':
    'The historical comparison is computed over cells with activity.',
  'calendar.svgNote.zero':
    'Zero is kept as zero; the dash indicates there is no data for this series.',
  'calendar.svgNote.partial': '* Month clipped by the queried range.',
  'calendar.svgNote.sensors':
    '“All sensors” is the union of sources, not an average: the series is dominated by VIIRS and detections from different sensors are not added together.',
  'calendar.svgNote.anomaly':
    '▼ marks months below −50% and ▲ those above +50% relative to the median of other complete years.',
  'calendar.svgFooter':
    'Chronos Fire · NASA FIRMS · MODIS + VIIRS · Generated on {date}',
  'calendar.filenamePrefix': 'calendar',
  'calendar.export.svgDone': 'SVG downloaded: {file}',
  'calendar.export.pngDone': 'PNG downloaded: {file}',
  'calendar.export.pngError': 'The PNG could not be generated.',

  'legend.sequentialTitle.cells':
    'Active cells · quintiles of the selection',
  'legend.sequentialTitle.hotspots':
    'Detected hotspots · quintiles of the selection',
  'legend.anomalyTitle':
    'Deviation from the median of other complete years',
  'legend.anomalyNote':
    'Fixed cuts at ±15% and ±50%. Purple–orange ramp (PuOr), colorblind-safe; ▼ and ▲ do not depend on color. Partial or unreferenced months are not compared and fall in the central range.',
  'legend.sequentialNote':
    'Five quintiles of the months with a value in the selection: each band groups 20% of the months and the legend shows its exact cuts. {name} ramp, perceptually uniform and colorblind-safe. Each cell\'s number is always the actual value of {metric}.',

  'modal.title': 'Fire activity calendar',
  'modal.hint':
    'Select a month to explore its active cells and daily detail.',
  'modal.closeAria': 'Close the calendar',

  'daily.kicker': 'ZOOM INTO THE DETAIL',
  'daily.title': 'Daily detail',
  'daily.unit.cells': 'active cells per day',
  'daily.unit.hotspots': 'detected hotspots per day',
  'daily.loading': 'Loading daily detail…',
  'daily.error': 'The daily detail could not be loaded.',
  'daily.empty': 'No daily data for this period.',
  'daily.noActivity.cells': 'No active cells recorded in this period.',
  'daily.noActivity.hotspots': 'No hotspots recorded in this period.',
  'daily.ariaChart': '{metric} per day in {month}',
  'daily.ariaSelected': 'the selected month',
  'daily.details': 'View accessible daily values',
  'daily.caption.cells':
    'Daily series of active cells; per-sensor hotspots are not averaged together.',
  'daily.caption.hotspots':
    'Daily series of detected hotspots; per-sensor hotspots are not averaged together.',
  'daily.date': 'UTC date',
  'daily.cells': 'Active cells',
  'daily.modis': 'MODIS hotspots',
  'daily.viirs': 'VIIRS hotspots',
  'daily.total': 'Total hotspots',

  'alerts.kicker': 'EARLY WARNING · NRT',
  'alerts.title': 'Recent alerts',
  'alerts.refresh': 'Refresh',
  'alerts.refreshing': 'Refreshing…',
  'alerts.updated.lessMinute': 'Updated less than a minute ago',
  'alerts.updated.minutes': 'Updated {n} min ago',
  'alerts.updated.hours': 'Updated {n} h ago',
  'alerts.summary':
    'Last {days} days · provisional data · latest detection {time}',
  'alerts.helpSummary': 'What do the levels and frequency mean?',
  'alerts.help.warning':
    'High priority: recurrence ≥ 50% or highly unusual activity (percentile ≥ 90%) with historical baseline or at least 10 detections.',
  'alerts.help.watch':
    'Watch: recurrence ≥ 25% or unusual activity (percentile ≥ 75%) with historical baseline or at least 3 detections.',
  'alerts.help.info':
    'No priority: activity within the expected range or without sufficient historical support.',
  'alerts.help.triage':
    'These are triage levels for prioritizing review; they are not an official alert or a prediction.',
  'alerts.help.formula':
    'Historical frequency is how often this 0.05° cell had activity in the same week of the year across the reference years (for example, 13 SNPP years). It is estimated with p = (k + α) / (n + α + β), where k is the years with fire and n the years with coverage.',
  'alerts.help.notPrediction':
    'This is not a prediction or an official alert: coverage is a regional proxy and the probability is a lower bound. With 13 years the interval is intentionally wide and is shown next to the value.',
  'alerts.loading': 'Querying alerts…',
  'alerts.empty': 'No NRT alerts in the area and period.',
  'alerts.detections': 'Detections',
  'alerts.power': 'Power',
  'alerts.frequency': 'Historical frequency',
  'alerts.seeInterval': 'View interval and reference years. {bar}',
  'alerts.ref.none': 'no reference years',
  'alerts.noInterval': 'no interval',
  'alerts.ref.one': '1 reference year',
  'alerts.ref.many': '{n} reference years',
  'alerts.percentile': 'Percentile {n}',
  'alerts.percentileTitle':
    'Recent activity: percentile {n} of the reference years',
  'alerts.tip.ci': '90% CI',
  'alerts.tip.reference': 'Reference',
  'alerts.tip.noCoverage': 'no years with coverage',
  'alerts.tip.coverage': '{n} years with coverage',
  'alerts.tip.activity':
    'Recent activity: percentile {n}; {n}% of reference years had fewer detections in this week.',
  'alerts.tip.disclaimer':
    'Estimated historical frequency: this is not a prediction or an official risk level.',
  'alerts.barLabel':
    '90% confidence interval: {interval}. Estimated frequency: {value}.',
  'alerts.sensors': 'Sensors',
  'alerts.pagerAria': 'Alerts pagination',
  'alerts.prevAria': 'Previous alerts page',
  'alerts.nextAria': 'Next alerts page',
  'alerts.aoiAnomaly': 'AOI anomaly: {value}×.',

  'validation.kicker': 'VALIDATION WITH EVENTS',
  'validation.title': 'Known events',
  'validation.intro':
    'It compares each real event with what usually happens in that same week. If the anomaly exceeds 1×, the data reproduce the peak.',
  'validation.howSummary': 'How it is calculated',
  'validation.howText':
    'Baseline: median of the same ISO week in the sensor reference years, excluding the evaluated year (leave-one-out). The anomaly indicates how many times that baseline was observed in the event week.',
  'validation.verdictAria': 'What each verdict means',
  'validation.verdict.pass': 'Pass',
  'validation.verdict.fail': 'Fail',
  'validation.verdict.insufficient': 'No data',
  'validation.help.pass':
    'The event is reproduced: anomaly above 1× with a reliable baseline.',
  'validation.help.fail':
    'The anomaly is 1× or less: the data do not show the expected peak.',
  'validation.help.insufficient':
    'There is nothing to compare with: reference years are missing or there are very few detections.',
  'validation.loading': 'Calculating validation…',
  'validation.error': 'Validation could not be loaded.',
  'validation.week': 'Week {week} · {year}',
  'validation.verdictTitle': 'Technical verdict: {verdict}',
  'validation.noAnomaly': 'no comparable anomaly',
  'validation.timesBaseline': 'times the baseline',
  'validation.observed': 'Observed',
  'validation.baseline': 'Baseline',
  'validation.pagerAria': 'Events pagination',
  'validation.prevAria': 'Previous events page',
  'validation.nextAria': 'Next events page',

  'method.kicker': 'FROM OBSERVATION TO COMPARISON',
  'method.title': 'What you are seeing',
  'method.coverage':
    '{coverage}. Presumed vegetation with nominal or high confidence.',
  'method.step1.strong': 'A common grid.',
  'method.step1.body':
    'Detections assigned by their center to 0.05° (~5 km) cells.',
  'method.step2.strong': 'A common day.',
  'method.step2.body':
    'Daily aggregation in UTC; a cell with activity on a given day counts once.',
  'method.step3.strong': 'A comparable series.',
  'method.step3.body':
    'The calendar uses cells with activity, not a sum of detections across sensors.',
  'method.definition.strong': 'Active cell ≠ hotspot ≠ fire.',
  'method.definition.body':
    'A cell can contain several hotspots; a hotspot is a satellite detection and a unique fire would require grouping events. The historical comparison is computed over cells with activity; hotspots are shown as detection counts.',
  'method.riskTitle': 'Historical probability per cell-week',
  'method.riskFormula':
    'For each 0.05° cell and ISO week, it estimates how often activity occurred in the reference years: p = (k + α) / (n + α + β), where k is the years with fire, n the years with coverage and α/β a weak weekly prior (κ = 8). The interval is Wilson at 90% over k/n.',
  'method.riskCoverage':
    'n uses a proxy coverage: a day counts as covered if the sensor recorded at least one detection in the region. It does not prove the absence of clouds, so the probability is a lower bound. With 13 years the interval is intentionally wide and is shown.',
  'method.riskLevels':
    'The activity percentile compares the detections of the last 7 days with the reference years for that cell-week. Levels prioritize triage: warning with p ≥ 50% or percentile ≥ 90% with historical baseline (p ≥ 10%) or at least 10 detections; watch with p ≥ 25% or percentile ≥ 75% with baseline or at least 3 detections; info otherwise. These are provisional thresholds.',
  'method.disclaimer':
    'It is not a prediction, it does not include weather or fuel, and it is not an official alert.',
  'method.calibration': 'Measured calibration (holdout {year}):',
  'method.calibrationLine':
    'Brier {brier} · BSS {bss} · AUC {auc} · {cells} cell-weeks.',
  'method.calibrationLow':
    'BSS is below the threshold {threshold}: it is published as {version} and no calibration is claimed.',
  'method.calibrationStability': 'Short window 2019–2024: BSS {bss}.',
  'method.audienceTitle': 'Who it is for',
  'method.audience.emergency.strong': 'Emergency response:',
  'method.audience.emergency.body':
    'spot critical weeks and compare the current month with its history.',
  'method.audience.science.strong': 'Scientists:',
  'method.audience.science.body':
    'explore seasonal patterns and multi-year anomalies.',
  'method.audience.land.strong': 'Land management:',
  'method.audience.land.body':
    'prioritize monitoring in areas with recurring activity.',
  'method.useCase':
    'Weekly validation case: Paraná Delta, week 40 of 2020: 6.6× the median (66 active cells vs. 10 reference).',
  'method.note':
    'Descriptive tool: info/watch/warning levels prioritize triage; they are not a prediction or an official alert.',
  'method.limits': 'Limits of the comparison',
  'method.limits1':
    "Confidence is harmonized as low, nominal and high. The baseline uses the multi-year median per AOI and each sensor's era.",
  'method.limits2':
    'There is no physical calibration of detection efficiency or FRP. Coverage is approximated with days with detections (coverage_daily); it does not discount cloud cover or overpasses. The absence of records does not confirm the absence of fire.',
  'method.provisional':
    '{period} is provisional NRT and is excluded from the baseline. ',
  'method.limits3':
    'A 0.05° cell does not represent burned area.',
  'method.firms': 'Learn about NASA FIRMS ↗',

  'comparison.kicker': 'PUT THE MONTH IN CONTEXT',
  'comparison.title': 'Against its history',
  'comparison.monthLine': '{month} {year} · {series}',
  'comparison.deltaNone':
    'There is no valid comparison for this partial or unreferenced period.',
  'comparison.deltaNote':
    'Difference from the median of the same month in other complete years of the selection.',
  'comparison.refYears':
    ' · {n} reference years · {confidence} confidence',
  'comparison.confidence.high': 'high',
  'comparison.confidence.low': 'low',
  'comparison.confidence.insufficient': 'insufficient',
  'comparison.selectedMonth': 'Selected month',
  'comparison.median': 'Reference median',
  'comparison.historicalComparison': 'Historical comparison',
  'comparison.cellsUnit': 'active cells',
  'comparison.hotspotsMonth':
    'Hotspots detected in the selected month. The historical comparison is computed over cells with activity; hotspots are detection counts and depend on the sensor constellation.',
  'comparison.hotspotsSelected': '{value} detected hotspots',
  'comparison.notAvailable': 'Not available for hotspots',
  'comparison.allSensors':
    'The “all sensors” series is not neutral: it is dominated by VIIRS.',
  'comparison.selectMonth': 'Select a month from the calendar.',
  'comparison.foot.cells':
    'Descriptive comparison for this series. It is not a prediction or a risk level.',
  'comparison.foot.hotspots':
    'Hotspots are shown as counts. They are not compared with a multi-year median to avoid confusing detections with fires.',

  'cell.noData': 'no data for this series',
  'cell.partial': ', partial period',
  'cell.delta': ', {sign}{pct}% vs. the median of other complete years',

  'band.low-extreme': '≤ −50%',
  'band.low': '−50 to −15%',
  'band.neutral': '±15%',
  'band.high': '+15 to +50%',
  'band.high-extreme': '≥ +50%',

  'risk.cuts': '0–1%, 1–3%, 3–8%, 8–20%, ≥ 20%',

  'color.ramp.volume': 'Observed volume',
  'color.ramp.probability': 'Historical probability',
  'color.ramp.anomaly': 'Difference from the median',

  'error.fetch': 'The query could not be loaded.',
  'error.range': 'The server did not report the date range.',
  'error.query': 'Error querying the data.',
  'error.daily': 'The daily detail could not be loaded.',
  'error.server': 'Server unavailable.',
  'error.alerts': 'Alerts could not be loaded.',
  'error.validation': 'Validation could not be loaded.',
  'error.csv': 'Could not verify the CSV.',
  'error.csvStatus': 'Could not verify the CSV (HTTP {status}).',
  'error.pngInvalid': 'The SVG has no valid dimensions to generate the PNG.',
  'error.pngRaster': 'The calendar SVG could not be rasterized.',
  'error.pngCanvas': 'The browser cannot generate the PNG.',
  'error.pngGenerate': 'The PNG could not be generated.',
}

export const MESSAGES = { es, en }

export const MONTHS: Record<
  'es' | 'en',
  { long: string[]; short: string[] }
> = {
  es: {
    long: [
      'Enero',
      'Febrero',
      'Marzo',
      'Abril',
      'Mayo',
      'Junio',
      'Julio',
      'Agosto',
      'Septiembre',
      'Octubre',
      'Noviembre',
      'Diciembre',
    ],
    short: ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'],
  },
  en: {
    long: [
      'January',
      'February',
      'March',
      'April',
      'May',
      'June',
      'July',
      'August',
      'September',
      'October',
      'November',
      'December',
    ],
    short: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
  },
}
