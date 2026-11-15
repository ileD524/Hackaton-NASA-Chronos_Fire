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
  anomaly2020: september2020.anomaly,
  anomalyConfidence: september2020.confidence,
  dailyRows: daily.length,
  heatmapHotspots,
  heatmapModisHotspots,
  hotspotsCsvRows: csvRows,
  alerts: alerts.count,
  validationEvents: validation.length,
}, null, 2))
