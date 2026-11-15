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
await expect(page.locator('.map-toolbar')).toContainText('hotspots (todas las fuentes)')
await expect(page.locator('.map-foot')).toContainText('no se suman como promedio')
await page.getByRole('button', { name: 'Ampliar mapa' }).click()
await expect(page.locator('.map-panel')).toHaveClass(/is-expanded/)
await expect(page.locator('.map-backdrop')).toBeVisible()
await expect(page.getByRole('button', { name: 'Cerrar', exact: true })).toBeVisible()
await page.keyboard.press('Escape')
await expect(page.locator('.map-panel')).not.toHaveClass(/is-expanded/)
await expect(page.getByRole('button', { name: 'Ampliar mapa' })).toBeFocused()
await expect(page.locator('.comparison-number')).not.toHaveText('—')
await expect(page.locator('section[aria-labelledby="comparison-title"]')).toContainText('años de referencia')
const initialCsv = await hotspotsCsvURL()
const initialFrom = await page.getByLabel('Desde', { exact: true }).inputValue()
const initialTo = await page.getByLabel('Hasta', { exact: true }).inputValue()
if (initialCsv.searchParams.get('bbox') !== '-61,-34,-59.5,-32.5') throw new Error('CSV inicial sin bbox del AOI')
if (initialCsv.searchParams.get('from') !== initialFrom) throw new Error('CSV inicial sin fecha desde')
await expect(page.getByText(/Hasta 50\.000 filas/)).toBeVisible()

const downloadPromise = page.waitForEvent('download')
await page.getByRole('link', { name: /Descargar hotspots CSV/ }).click()
const download = await downloadPromise
expect(download.url()).toContain('/api/detections/export.csv')
expect(download.suggestedFilename()).toBe('chronosfire-detecciones.csv')
await expect(page.locator('.export-note[role="status"]')).toContainText(/Descarga (completa|limitada)/)

await page.screenshot({ path: `${ARTIFACTS}/desktop.png`, fullPage: true })

const svgDownloadPromise = page.waitForEvent('download')
await page.getByRole('button', { name: 'Descargar el calendario en SVG' }).click()
const svgDownload = await svgDownloadPromise
const svgFilename = svgDownload.suggestedFilename()
if (svgFilename !== `chronos-fire-calendario-delta-del-parana-${initialFrom}-${initialTo}.svg`) {
  throw new Error(`nombre de SVG inesperado: ${svgFilename}`)
}
const svgPath = await svgDownload.path()
if (!svgPath) throw new Error('descarga SVG sin archivo temporal')
const svg = fs.readFileSync(svgPath, 'utf8')
if (!svg.startsWith('<?xml')) throw new Error('SVG sin declaracion XML')
if (!/<svg[^>]+viewBox="0 0 1120 \d+"/.test(svg)) throw new Error('SVG sin viewBox esperado')
if (!svg.includes('Calendario de actividad de fuego')) throw new Error('SVG sin titulo')
if (!svg.includes('Delta del Paraná')) throw new Error('SVG sin AOI')
if (!svg.includes('Todos los sensores')) throw new Error('SVG sin sensor activo')
if (!svg.includes('No mide área quemada')) throw new Error('SVG sin nota metodologica')
if (!svg.includes('<desc id="calendar-svg-desc">')) throw new Error('SVG sin descripcion accesible')
if (!svg.includes('Celdas con actividad · quintiles de la selección')) throw new Error('SVG sin leyenda')
const cellsOnScreen = await page.locator('.cal-cell').count()
const cellsInSvg = (svg.match(/class="calendar-cell-value"/g) || []).length
if (cellsInSvg !== cellsOnScreen) throw new Error(`SVG ${cellsInSvg} celdas != pantalla ${cellsOnScreen}`)
if ((svg.match(/class="calendar-cell-bg"/g) || []).length !== cellsOnScreen) {
  throw new Error('SVG sin fondo para cada celda')
}
if ((svg.match(/class="calendar-legend-item"/g) || []).length !== 5) {
  throw new Error('SVG sin los cinco tramos de leyenda')
}

const selectedLabel = (await page.locator('#mapa-title').textContent())?.trim() ?? ''
const selectedCell = page.locator(`.cal-cell[aria-label^="${selectedLabel}:"]`).first()
const screenValue = (await selectedCell.textContent())?.trim().replace('*', '')
const screenColor = await selectedCell.evaluate((element) => getComputedStyle(element).backgroundColor)
const screenHex = `#${(screenColor.match(/\d+/g) ?? []).slice(0, 3)
  .map((part) => Number(part).toString(16).padStart(2, '0')).join('').toUpperCase()}`
const selectedGroup = svg.split('<g>').find((chunk) => chunk.includes(`<title>${selectedLabel}:`))
const svgValue = selectedGroup?.match(/class="calendar-cell-value"[^>]*>([^<]*)</)?.[1]
if (!selectedGroup) throw new Error(`SVG sin la celda seleccionada ${selectedLabel}`)
if (svgValue !== screenValue) throw new Error(`SVG "${svgValue}" != pantalla "${screenValue}"`)
if (!selectedGroup.includes(`fill="${screenHex}"`)) {
  throw new Error(`SVG con color ${selectedGroup.match(/fill="(#[0-9A-F]{6})"/)?.[1]} != pantalla ${screenHex}`)
}
await expect(page.locator('.export-status')).toContainText('SVG descargado')

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
const hotspotsSvgPromise = page.waitForEvent('download')
await page.getByRole('button', { name: 'Descargar el calendario en SVG' }).click()
const hotspotsSvgDownload = await hotspotsSvgPromise
const hotspotsSvgPath = await hotspotsSvgDownload.path()
const hotspotsSvg = hotspotsSvgPath ? fs.readFileSync(hotspotsSvgPath, 'utf8') : ''
if (!hotspotsSvg.includes('hotspots detectados por mes')) throw new Error('SVG de hotspots sin metrica')
if (!hotspotsSvg.includes('Hotspots detectados · quintiles de la selección')) {
  throw new Error('SVG de hotspots sin leyenda de su metrica')
}
if (!hotspotsSvg.includes('sin focos industriales')) throw new Error('SVG de hotspots sin definicion')
if (!hotspotsSvg.includes('no se suman')) throw new Error('SVG de hotspots sin aviso cross-sensor')
if (hotspotsSvg.includes('Desviación respecto de la mediana')) {
  throw new Error('SVG de hotspots conserva la anomalia de celdas')
}
await page.getByText('Ver valores diarios accesibles').click()
await expect(page.locator('.daily-table thead')).toContainText('Hotspots MODIS')
await expect(page.locator('.daily-table thead')).toContainText('Total hotspots')
await page.getByRole('button', { name: 'Celdas con actividad', exact: true }).click()
await expect(page.locator('.kpi-list')).toContainText('celdas-mes con actividad')
await expect(page.locator('.daily-table thead')).toContainText('Celdas con actividad')
await expect(page.locator('#calendario .cal-cell[aria-label^="Agosto 2020:"]').first()).toHaveText(cellsValue)

// F-01: selector de sensor
const sensorSelect = page.getByLabel('Sensor', { exact: true })
const allCell = page.locator('#calendario .cal-cell[aria-label^="Agosto 2020:"]').first()
const allCellValue = (await allCell.textContent())?.trim()
if (!allCellValue) throw new Error('sin valor inicial para Agosto 2020')
await sensorSelect.selectOption('MODIS')
await expect(page.locator('.series-badge').first()).toContainText('MODIS observado')
await expect(page.locator('.map-toolbar')).toContainText('Serie: MODIS')
await expect(allCell).not.toHaveText(allCellValue)
await sensorSelect.selectOption('all')
await expect(page.locator('.series-badge').first()).toContainText('Todos los sensores')
await expect(page.locator('.map-toolbar')).toContainText('Serie: Todos los sensores')
await expect(allCell).toHaveText(allCellValue)

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

await page.getByRole('button', { name: /Ver todo el archivo \d{4}–\d{4}/ }).click()
const archiveFrom = await page.getByLabel('Desde', { exact: true }).inputValue()
const archiveTo = await page.getByLabel('Hasta', { exact: true }).inputValue()
const expectedCells = (Number(archiveTo.slice(0, 4)) - Number(archiveFrom.slice(0, 4)) + 1) * 12
await expect(page.locator('.cal-cell')).toHaveCount(expectedCells, { timeout: 120000 })
const fullCsv = await hotspotsCsvURL()
if (fullCsv.searchParams.has('bbox')) throw new Error('CSV nacional conserva bbox')
if (fullCsv.searchParams.get('from') !== archiveFrom) throw new Error('CSV nacional sin fecha desde')
await page.getByRole('button', { name: 'Diferencia histórica', exact: true }).click()
await expect(page.locator('.legend')).toContainText('Desviación')

const anomalySvgPromise = page.waitForEvent('download')
await page.getByRole('button', { name: 'Descargar el calendario en SVG' }).click()
const anomalySvgDownload = await anomalySvgPromise
const anomalyFilename = anomalySvgDownload.suggestedFilename()
if (anomalyFilename !== `chronos-fire-calendario-extension-completa-argentina-${archiveFrom}-${archiveTo}.svg`) {
  throw new Error(`nombre de SVG de anomalia inesperado: ${anomalyFilename}`)
}
const anomalySvgPath = await anomalySvgDownload.path()
const anomalySvg = anomalySvgPath ? fs.readFileSync(anomalySvgPath, 'utf8') : ''
if (!anomalySvg.includes('Desviación respecto de la mediana')) {
  throw new Error('SVG de anomalia sin titulo de leyenda')
}
if (!anomalySvg.includes('≥ +50%')) throw new Error('SVG de anomalia sin cortes')
if (!anomalySvg.includes('▲')) throw new Error('SVG de anomalia sin simbolo accesible')

await expect(page.locator('#alertas')).toContainText('Alertas recientes')
await expect(page.locator('#alertas')).toContainText(/Actualizado hace/)
await expect(page.locator('#alertas .alert-card')).toHaveCount(4)
await expect(page.locator('#alertas .pager')).toContainText(/Página 1 de \d+/)
await page.getByRole('button', { name: 'Siguiente página de alertas' }).click()
await expect(page.locator('#alertas .pager')).toContainText('Página 2 de')
await page.getByRole('button', { name: 'Página anterior de alertas' }).click()
const refreshAlerts = page.getByRole('button', { name: 'Actualizar', exact: true })
await expect(refreshAlerts).toBeVisible()
const alertResponse = page.waitForResponse((response) =>
  response.url().includes('/api/alerts') && response.request().method() === 'GET')
await refreshAlerts.click()
await alertResponse
await expect(page.locator('#alertas')).toContainText(/Actualizado hace/)
await expect(page.locator('#validacion')).toContainText('Eventos conocidos')
await expect(page.locator('#validacion')).toContainText('Delta del Parana', { timeout: 30000 })
await expect(page.locator('#validacion .event-card')).toHaveCount(3)
await expect(page.locator('#validacion .event-card').first()).toContainText('Cordoba')
await expect(page.locator('#validacion .event-card .bar-observed')).toHaveCount(3)
await expect(page.locator('#validacion details p')).toBeHidden()
await expect(page.locator('#validacion .verdict-legend')).toContainText('Pasa')
await expect(page.locator('#validacion .verdict-legend')).toContainText('No pasa')
await expect(page.locator('#validacion .verdict-legend')).toContainText('Sin datos')
const cardsMatchPanel = await page.locator('#validacion').evaluate((panel) =>
  [...panel.querySelectorAll('.event-card')].every((card) => {
    const outer = panel.getBoundingClientRect()
    const inner = card.getBoundingClientRect()
    return inner.left >= outer.left && inner.right <= outer.right
  }))
expect(cardsMatchPanel).toBe(true)
await expect(page.locator('#validacion .pager')).toContainText('Página 1 de 2')
await page.getByRole('button', { name: 'Siguiente página de eventos' }).click()
await expect(page.locator('#validacion .pager')).toContainText('Página 2 de 2')
await page.getByRole('button', { name: 'Página anterior de eventos' }).click()

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
    'calendar SVG download with values, legend and note',
    'calendar SVG anomaly palette',
    'calendar SVG follows hotspot mode',
    'cells and hotspots metric toggle',
    'hotspots daily table by sensor',
    'sensor selector changes series and calendar',
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
