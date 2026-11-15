import { chromium, expect } from '@playwright/test'
import fs from 'node:fs'

const BASE_URL = process.env.BASE_URL || 'http://127.0.0.1:5173/'
const ARTIFACTS = process.env.ARTIFACTS_DIR || 'artifacts'
fs.mkdirSync(ARTIFACTS, { recursive: true })

const browser = await chromium.launch({
  executablePath: process.env.CHROMIUM_PATH || undefined,
  headless: true,
  args: ['--no-sandbox'],
})
const page = await browser.newPage({ viewport: { width: 1440, height: 1050 }, locale: 'es-AR' })
const errors = []
page.on('pageerror', (error) => errors.push(error.message))
page.on('console', (message) => { if (message.type() === 'error') errors.push(message.text()) })

async function hotspotsCsvURL() {
  const link = page.getByRole('link', { name: /Descargar hotspots CSV/ })
  if (!(await link.isVisible())) throw new Error('boton de hotspots CSV no visible')
  const href = await link.getAttribute('href')
  if (!href) throw new Error('boton de hotspots CSV sin href')
  const url = new URL(href, BASE_URL)
  if (url.pathname !== '/api/detections/export.csv') throw new Error(`ruta CSV inesperada: ${url.pathname}`)
  if (url.searchParams.get('vegetation') !== 'true') throw new Error('CSV sin vegetation=true')
  if (url.searchParams.getAll('confidence').join(',') !== 'nominal,high') {
    throw new Error('CSV sin confianza nominal/alta')
  }
  if (url.searchParams.get('limit') !== '50000') throw new Error('CSV sin limit=50000')
  return url
}

await page.goto(BASE_URL, { waitUntil: 'networkidle' })
await expect(page.locator('.kpi-value').first()).toContainText(/\d/)
await expect(page.locator('.kpi-list')).toContainText('celdas-mes con actividad')
await expect(page.locator('#mapa-title')).toHaveText('Agosto 2020')
await expect(page.locator('.map-toolbar')).toContainText('hotspots en')
await expect(page.locator('.map-foot')).toContainText('no se suman como promedio')
await expect(page.locator('.comparison-number')).not.toHaveText('—')
await expect(page.getByText(/años de referencia/).first()).toBeVisible()
const initialCsv = await hotspotsCsvURL()
if (initialCsv.searchParams.get('bbox') !== '-61,-34,-59.5,-32.5') throw new Error('CSV inicial sin bbox del AOI')
if (initialCsv.searchParams.get('from') !== '2012-01-01') throw new Error('CSV inicial sin fecha desde')
await expect(page.getByText(/Hasta 50\.000 filas/)).toBeVisible()

// F-02: modo celdas / hotspots
const augustCell = page.locator('#calendario .cal-cell[aria-label^="Agosto 2020:"]').first()
const cellsValue = (await augustCell.textContent())?.trim()
await page.getByRole('button', { name: 'Hotspots detectados', exact: true }).click()
await expect(page.locator('.kpi-list')).toContainText('hotspots detectados en el rango')
await expect(page.locator('.legend')).toContainText('Hotspots detectados · quintiles')
await expect(page.getByRole('button', { name: 'Diferencia histórica', exact: true })).toHaveCount(0)
await expect(page.locator('#calendario')).toContainText('La comparación histórica y la rampa de anomalía se calculan sobre celdas con actividad')
await expect(page.locator('.comparison-number')).not.toContainText('%')
const hotspotsValue = (await augustCell.textContent())?.trim()
if (!cellsValue || !hotspotsValue || cellsValue === hotspotsValue) {
  throw new Error(`el calendario no cambió de métrica: ${cellsValue} -> ${hotspotsValue}`)
}
await page.getByText('Ver valores diarios accesibles').click()
await expect(page.locator('.daily-table thead')).toContainText('Hotspots MODIS')
await expect(page.locator('.daily-table thead')).toContainText('Total hotspots')
await page.getByRole('button', { name: 'Celdas con actividad', exact: true }).click()
await expect(page.locator('.kpi-list')).toContainText('celdas-mes con actividad')
await expect(page.locator('.daily-table thead')).toContainText('Celdas con actividad')
await expect(page.locator('#calendario .cal-cell[aria-label^="Agosto 2020:"]').first()).toHaveText(cellsValue)

const downloadPromise = page.waitForEvent('download')
await page.getByRole('link', { name: /Descargar hotspots CSV/ }).click()
const download = await downloadPromise
expect(download.url()).toContain('/api/detections/export.csv')
expect(download.suggestedFilename()).toBe('chronosfire-detecciones.csv')
await expect(page.locator('.export-note[role="status"]')).toContainText(/Descarga (completa|limitada)/)

await page.screenshot({ path: `${ARTIFACTS}/desktop.png`, fullPage: true })

await page.getByRole('button', { name: 'Abrir el calendario', exact: true }).click()
await expect(page.getByRole('dialog')).toBeVisible()
await page.getByRole('dialog').getByRole('button', { name: /Febrero 2022:/ }).click()
await expect(page.getByRole('dialog')).not.toBeVisible()
await expect(page.locator('#mapa-title')).toHaveText('Febrero 2022')

await page.getByLabel('Área', { exact: true }).selectOption('corrientes')
await page.getByRole('button', { name: /Actualizar calendario/ }).click()
await expect(page.locator('.hero-context')).toContainText('Área personalizada')
await expect(page.locator('.series-badge').first()).toContainText('Todos los sensores')
const corrientesCsv = await hotspotsCsvURL()
if (corrientesCsv.searchParams.get('bbox') !== '-60,-30.8,-55.5,-27') {
  throw new Error(`CSV no sigue el AOI activo: ${corrientesCsv.searchParams.get('bbox')}`)
}
await page.screenshot({ path: `${ARTIFACTS}/corrientes.png`, fullPage: true })

await page.getByRole('button', { name: 'Ver todo el archivo 2003–2026' }).click()
await expect(page.locator('.cal-cell')).toHaveCount(288, { timeout: 60000 })
const fullCsv = await hotspotsCsvURL()
if (fullCsv.searchParams.has('bbox')) throw new Error('CSV nacional conserva bbox')
if (fullCsv.searchParams.get('from') !== '2003-01-01') throw new Error('CSV nacional sin fecha desde')
await page.getByRole('button', { name: 'Diferencia histórica', exact: true }).click()
await expect(page.locator('.legend')).toContainText('Desviación')

await expect(page.locator('#alertas')).toContainText('Alertas recientes')
await expect(page.locator('#validacion')).toContainText('Eventos conocidos')
await expect(page.locator('#validacion')).toContainText('Delta del Parana', { timeout: 30000 })

await page.getByRole('button', { name: 'Abrir el calendario', exact: true }).click()
await page.keyboard.press('Escape')
await expect(page.getByRole('dialog')).not.toBeVisible()
await expect(page.getByRole('button', { name: 'Abrir el calendario', exact: true })).toBeFocused()

await page.setViewportSize({ width: 390, height: 844 })
await page.screenshot({ path: `${ARTIFACTS}/mobile.png`, fullPage: true })
expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
expect(errors).toEqual([])

const badContrast = await page.locator('.cal-cell:not(:disabled)').evaluateAll((elements) => {
  const luminance = (color) => {
    const values = color.match(/[\d.]+/g).slice(0, 3).map(Number).map((x) => x / 255)
      .map((x) => x <= .04045 ? x / 12.92 : ((x + .055) / 1.055) ** 2.4)
    return values[0] * .2126 + values[1] * .7152 + values[2] * .0722
  }
  return elements.filter((el) => {
    const style = getComputedStyle(el)
    const a = luminance(style.color)
    const b = luminance(style.backgroundColor)
    return (Math.max(a, b) + .05) / (Math.min(a, b) + .05) < 4.5
  }).length
})
expect(badContrast).toBe(0)

fs.writeFileSync(`${ARTIFACTS}/browser-results.json`, JSON.stringify({
  status: 'PASS',
  errors,
  mobileOverflow: false,
  heatmapContrastFailures: badContrast,
  checks: [
    'initial monthly calendar',
    'map hotspots by sensor',
    'CSV download with truncation notice',
    'cells and hotspots metric toggle',
    'hotspots daily table by sensor',
    'backend monthly anomaly',
    'hotspots CSV button follows active filters',
    'month selection updates map',
    'AOI filter',
    'full history',
    'shared color mode',
    'alerts panel',
    'validation panel',
    'modal Escape and focus restoration',
    'mobile layout',
    'heatmap contrast',
  ],
}, null, 2))
console.log('PASS: frontend Java contract, calendar, alerts, validation, mobile and contrast')
await browser.close()
