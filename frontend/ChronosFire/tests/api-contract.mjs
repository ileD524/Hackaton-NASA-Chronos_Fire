const BASE = process.env.API_BASE || 'http://127.0.0.1:8080'

async function get(path) {
  const response = await fetch(`${BASE}${path}`)
  if (!response.ok) throw new Error(`${path} -> HTTP ${response.status}`)
  return response.json()
}

const health = await get('/api/health')
if (!health.database) throw new Error('health.database es false')

const meta = await get('/api/meta')
if (!Array.isArray(meta.sensors) || meta.sensors.length !== 8) throw new Error('meta.sensors != 8')

const bbox = '-61,-34,-59.5,-32.5'

// F-10: titular semanal Delta 2020-W40 (AUTO resuelve a VIIRS SNPP).
const weekly = await get(`/api/calendar/weekly?bbox=${bbox}&year=2020&week=40&sensor=AUTO`)
if (weekly.observedFireCells !== 66) throw new Error(`weekly observedFireCells ${weekly.observedFireCells} != 66`)
if (weekly.baselineFireCells !== 10) throw new Error(`weekly baselineFireCells ${weekly.baselineFireCells} != 10`)
if (Math.abs(weekly.anomaly - 6.6) > 1e-9) throw new Error(`weekly anomaly ${weekly.anomaly} != 6.6`)
if (weekly.referenceYears !== 12) throw new Error(`weekly referenceYears ${weekly.referenceYears} != 12`)
if (weekly.confidence !== 'high') throw new Error(`weekly confidence ${weekly.confidence} != high`)
if (weekly.fireCellsBySensor?.VIIRS_SNPP !== 66) {
  throw new Error(`weekly fireCellsBySensor.VIIRS_SNPP ${weekly.fireCellsBySensor?.VIIRS_SNPP} != 66`)
}
if (weekly.dataStatus !== 'observed') throw new Error(`weekly dataStatus ${weekly.dataStatus} != observed`)
if (typeof weekly.nDataYears !== 'number' || weekly.nDataYears <= 0) {
  throw new Error(`weekly nDataYears invalido: ${weekly.nDataYears}`)
}

// D-01: una semana previa a la era del sensor no debe mostrarse como "sin fuego".
const weeklyNoData = await get(`/api/calendar/weekly?bbox=${bbox}&year=2000&week=1&sensor=VIIRS_SNPP`)
if (weeklyNoData.dataStatus !== 'no_data') {
  throw new Error(`weekly pre-SNPP dataStatus ${weeklyNoData.dataStatus} != no_data`)
}
if (typeof weeklyNoData.nDataYears !== 'number') {
  throw new Error(`weekly pre-SNPP nDataYears invalido: ${weeklyNoData.nDataYears}`)
}

// F-10: tendencia anual 2019-2020 con anomalia calculable.
const trend = await get(`/api/calendar/trend?bbox=${bbox}&from=2019-01-01&to=2020-12-31&sensor=AUTO`)
if (trend.length !== 2) throw new Error(`trend != 2 anios: ${trend.length}`)
const trendByYear = new Map(trend.map((row) => [row.year, row]))
for (const year of [2019, 2020]) {
  const row = trendByYear.get(year)
  if (!row) throw new Error(`trend sin ${year}`)
  for (const field of ['fireCells', 'baselineFireCells', 'anomaly', 'referenceYears', 'confidence']) {
    if (!(field in row)) throw new Error(`trend ${year} sin ${field}`)
  }
  if (row.anomaly === null || row.confidence === 'insufficient') {
    throw new Error(`trend ${year} sin anomalia calculable`)
  }
}
if (!(trendByYear.get(2020).anomaly > trendByYear.get(2019).anomaly)) {
  throw new Error('trend 2020 no supera la anomalia de 2019')
}

const monthly = await get(`/api/calendar/monthly?bbox=${bbox}&from=2020-01-01&to=2020-03-31&sensor=ALL`)
if (monthly.length !== 3) throw new Error('monthly != 3 meses')
for (const field of ['hotspots', 'hotspotsBySensor', 'baselineFireCells', 'anomaly', 'referenceYears', 'confidence']) {
  if (!(field in monthly[0])) throw new Error(`monthly sin ${field}`)
}

const monthlyAnomaly = await get(`/api/calendar/monthly?bbox=${bbox}&from=2013-01-01&to=2023-12-31&sensor=VIIRS_SNPP`)
const september2020 = monthlyAnomaly.find((m) => m.year === 2020 && m.month === 8)
if (!september2020 || september2020.anomaly === null || september2020.confidence === 'insufficient') {
  throw new Error('monthly sin anomalia calculable para Delta 2020-09')
}

// F-10: el filtro por sensor cambia los valores del mismo mes.
const septemberAll = await get(`/api/calendar/monthly?bbox=${bbox}&from=2020-09-01&to=2020-09-30&sensor=ALL`)
const septemberSnpp = await get(`/api/calendar/monthly?bbox=${bbox}&from=2020-09-01&to=2020-09-30&sensor=VIIRS_SNPP`)
if (septemberAll.length !== 1 || septemberSnpp.length !== 1) {
  throw new Error(`monthly 2020-09 != 1 fila: ALL ${septemberAll.length}, SNPP ${septemberSnpp.length}`)
}
const allMonth = septemberAll[0]
const snppMonth = septemberSnpp[0]
if (allMonth.fireCells !== 189) throw new Error(`monthly ALL fireCells ${allMonth.fireCells} != 189`)
if (allMonth.hotspots !== 6628) throw new Error(`monthly ALL hotspots ${allMonth.hotspots} != 6628`)
if (snppMonth.fireCells !== 154) throw new Error(`monthly SNPP fireCells ${snppMonth.fireCells} != 154`)
if (snppMonth.hotspots !== 3313) throw new Error(`monthly SNPP hotspots ${snppMonth.hotspots} != 3313`)
if (allMonth.fireCells === snppMonth.fireCells && allMonth.hotspots === snppMonth.hotspots) {
  throw new Error('monthly ALL y SNPP devuelven los mismos valores')
}
for (const row of [allMonth, snppMonth]) {
  for (const field of ['hotspots', 'hotspotsBySensor', 'detectionsBySensor']) {
    if (!(field in row)) throw new Error(`monthly 2020-09 sin ${field}`)
  }
}

// D-01: un mes previo a la era del sensor debe quedar como no_data, no como cero.
const monthlyNoData = await get(
  `/api/calendar/monthly?bbox=${bbox}&from=2000-01-01&to=2000-01-31&sensor=VIIRS_NOAA21`)
if (monthlyNoData.length !== 1 || monthlyNoData[0].dataStatus !== 'no_data') {
  throw new Error(`monthly pre-NOAA21 dataStatus ${monthlyNoData[0]?.dataStatus} != no_data`)
}

const daily = await get(`/api/calendar/daily?bbox=${bbox}&from=2020-09-01&to=2020-09-30&sensor=ALL&limit=10`)
if (!daily.length) throw new Error('daily vacío')

const heatmap = await get(`/api/calendar/heatmap?bbox=${bbox}&from=2020-09-01&to=2020-09-30&sensor=ALL&limit=5000`)
if (!heatmap.length) throw new Error('heatmap vacío')
for (const field of ['hotspots', 'hotspotsBySensor']) {
  if (!(field in heatmap[0])) throw new Error(`heatmap sin ${field}`)
}
const heatmapHotspots = heatmap.reduce((sum, cell) => sum + cell.hotspots, 0)
if (heatmapHotspots !== 6628) throw new Error(`heatmap hotspots ${heatmapHotspots} != 6628`)

const heatmapModis = await get(`/api/calendar/heatmap?bbox=${bbox}&from=2020-09-01&to=2020-09-30&sensor=MODIS&limit=5000`)
const heatmapModisHotspots = heatmapModis.reduce((sum, cell) => sum + cell.hotspots, 0)
if (heatmapModisHotspots !== 704) throw new Error(`heatmap MODIS ${heatmapModisHotspots} != 704`)

const csvResponse = await fetch(
  `${BASE}/api/detections/export.csv?bbox=${bbox}&from=2020-09-01&to=2020-09-30&sensor=ALL`
  + '&vegetation=true&confidence=nominal&confidence=high&limit=50000',
)
if (!csvResponse.ok) throw new Error(`export.csv -> HTTP ${csvResponse.status}`)
if (!/text\/csv/.test(csvResponse.headers.get('content-type') || '')) {
  throw new Error('export.csv no es text/csv')
}
const disposition = csvResponse.headers.get('content-disposition') || ''
if (!/attachment/.test(disposition) || !/chronosfire-detecciones\.csv/.test(disposition)) {
  throw new Error(`Content-Disposition inesperado: ${disposition}`)
}
const csvBytes = Buffer.from(await csvResponse.arrayBuffer())
if (csvBytes[0] !== 0xef || csvBytes[1] !== 0xbb || csvBytes[2] !== 0xbf) {
  throw new Error('export.csv sin BOM UTF-8')
}
const csvLines = csvBytes.toString('utf8').replace(/^\uFEFF/, '').split(/\r?\n/)
const csvHeader = csvLines[0]
if (!csvHeader.startsWith('sensor,source,acquired_at,acq_date,latitude,longitude')) {
  throw new Error(`cabecera CSV inesperada: ${csvHeader}`)
}
const csvRows = csvLines.filter(Boolean).length - 1
if (csvRows <= 0 || csvRows > 50000) throw new Error(`filas CSV fuera de rango: ${csvRows}`)
if (csvResponse.headers.get('x-row-limit') !== '50000') throw new Error('X-Row-Limit inesperado')
if (csvResponse.headers.get('x-truncated') !== 'false') throw new Error('X-Truncated inesperado')
if (Number(csvResponse.headers.get('x-returned-rows')) !== csvRows) {
  throw new Error('X-Returned-Rows no coincide con las filas')
}

const truncatedResponse = await fetch(
  `${BASE}/api/detections/export.csv?bbox=${bbox}&from=2020-09-01&to=2020-09-30&sensor=ALL&limit=10`,
)
if (!truncatedResponse.ok) throw new Error(`export.csv truncado -> HTTP ${truncatedResponse.status}`)
if (truncatedResponse.headers.get('x-truncated') !== 'true') {
  throw new Error('X-Truncated no marco la descarga limitada')
}
if (Number(truncatedResponse.headers.get('x-returned-rows')) !== 10) {
  throw new Error('X-Returned-Rows no respeto el limite')
}
await truncatedResponse.text()

const calendarCsv = await fetch(
  `${BASE}/api/calendar/export.csv?bbox=${bbox}&from=2020-09-01&to=2020-09-30&sensor=ALL&limit=10`,
)
if (!calendarCsv.ok) throw new Error(`calendar export.csv -> HTTP ${calendarCsv.status}`)
if (calendarCsv.headers.get('x-truncated') !== 'true') {
  throw new Error('calendar X-Truncated no marco la descarga limitada')
}
if (Number(calendarCsv.headers.get('x-returned-rows')) !== 10) {
  throw new Error('calendar X-Returned-Rows no respeto el limite')
}
await calendarCsv.text()

const longCsvHead = await fetch(
  `${BASE}/api/detections/export.csv?bbox=${bbox}&from=2012-01-01&to=2026-11-14&sensor=ALL`
  + '&vegetation=true&confidence=nominal&confidence=high&limit=50000',
  { method: 'HEAD' },
)
if (!longCsvHead.ok) throw new Error(`long export.csv HEAD -> HTTP ${longCsvHead.status}`)
if (longCsvHead.headers.get('x-truncated') !== 'true') {
  throw new Error('long export.csv sin X-Truncated=true')
}
if (Number(longCsvHead.headers.get('x-returned-rows')) !== 50000) {
  throw new Error('long export.csv sin 50.000 filas')
}

const alerts = await get(`/api/alerts?bbox=${bbox}&days=7&limit=5`)
if (typeof alerts.count !== 'number' || !Array.isArray(alerts.alerts)) throw new Error('alerts inválido')

const validation = await get('/api/validation')
if (!validation.some((event) => event.aoiId === 'delta_2020' && event.verdict === 'pass')) {
  throw new Error('validation sin Delta pass')
}

console.log(JSON.stringify({
  status: 'PASS',
  health: health.status,
  sensors: meta.sensors.length,
  months: monthly.length,
  weekly: {
    fireCells: weekly.observedFireCells,
    baseline: weekly.baselineFireCells,
    anomaly: weekly.anomaly,
    nDataYears: weekly.nDataYears,
    dataStatus: weekly.dataStatus,
  },
  weeklyNoData: weeklyNoData.dataStatus,
  trendYears: trend.map((row) => row.year),
  monthlySeptember: { allHotspots: allMonth.hotspots, snppHotspots: snppMonth.hotspots },
  monthlyNoData: monthlyNoData[0].dataStatus,
  anomaly2020: september2020.anomaly,
  anomalyConfidence: september2020.confidence,
  dailyRows: daily.length,
  heatmapHotspots,
  heatmapModisHotspots,
  hotspotsCsvRows: csvRows,
  csvTruncationHeaders: true,
  longCsvHead: true,
  alerts: alerts.count,
  validationEvents: validation.length,
}, null, 2))
