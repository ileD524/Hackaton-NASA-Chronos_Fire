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
  alerts: alerts.count,
  validationEvents: validation.length,
}, null, 2))
