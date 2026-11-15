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

const legendContrast = () => page.locator('.legend-steps > span').evaluateAll((elements) => {
  const luminance = (color) => {
    const values = color.match(/[\d.]+/g).slice(0, 3).map(Number).map((x) => x / 255)
      .map((x) => x <= .04045 ? x / 12.92 : ((x + .055) / 1.055) ** 2.4)
    return values[0] * .2126 + values[1] * .7152 + values[2] * .0722
  }
  return elements.map((element) => {
    const style = getComputedStyle(element)
    const a = luminance(style.color)
    const b = luminance(style.backgroundColor)
    return (Math.max(a, b) + .05) / (Math.min(a, b) + .05)
  })
})

await page.goto(BASE_URL, { waitUntil: 'networkidle' })
await expect(page.locator('.cal-cell').first()).toBeVisible()
await expect(page.locator('.map-legend')).toContainText('Intensidad de hotspots')
await expect(page.locator('.map-foot')).toContainText('Doble codificación')
if ((await page.locator('.map-legend .ramp i').count()) !== 5) {
  throw new Error('el mapa no muestra los cinco tramos de la rampa')
}

const sequentialContrast = await legendContrast()
if (sequentialContrast.some((ratio) => ratio < 4.5)) {
  throw new Error(`contraste insuficiente en la leyenda secuencial: ${sequentialContrast.join(', ')}`)
}

const svgDownloadPromise = page.waitForEvent('download')
await page.getByRole('button', { name: 'Descargar el calendario en SVG' }).click()
const svgDownload = await svgDownloadPromise
const svgPath = await svgDownload.path()
if (!svgPath) throw new Error('descarga SVG sin archivo temporal')
const svg = fs.readFileSync(svgPath, 'utf8')
await svgDownload.saveAs(`${ARTIFACTS}/calendario.svg`)
const svgHeight = Number(svg.match(/<svg[^>]*\bheight="(\d+)"/)?.[1])
if (!svgHeight) throw new Error('SVG sin alto declarado')

const pngDownloadPromise = page.waitForEvent('download')
await page.getByRole('button', { name: 'Descargar el calendario en PNG' }).click()
const pngDownload = await pngDownloadPromise
const pngPath = await pngDownload.path()
if (!pngPath) throw new Error('descarga PNG sin archivo temporal')
const png = fs.readFileSync(pngPath)
await pngDownload.saveAs(`${ARTIFACTS}/calendario.png`)
if (png[0] !== 0x89 || png[1] !== 0x50 || png[2] !== 0x4e || png[3] !== 0x47) {
  throw new Error('el archivo descargado no es un PNG')
}
const pngWidth = png.readUInt32BE(16)
const pngHeight = png.readUInt32BE(20)
if (pngWidth !== 2240) throw new Error(`ancho PNG inesperado: ${pngWidth}`)
if (pngHeight !== svgHeight * 2) throw new Error(`alto PNG ${pngHeight} != SVG ${svgHeight} * 2`)
if (png.length < 80_000) throw new Error(`PNG sospechosamente chico: ${png.length} bytes`)
const expectedPng = svgDownload.suggestedFilename().replace(/\.svg$/, '.png')
if (pngDownload.suggestedFilename() !== expectedPng) {
  throw new Error(`nombre PNG inesperado: ${pngDownload.suggestedFilename()}`)
}
await expect(page.locator('.export-status')).toContainText('PNG descargado')
await expect(page.locator('.export-error')).toHaveCount(0)

await page.getByRole('button', { name: 'Diferencia histórica', exact: true }).click()
await expect(page.locator('.legend')).toContainText('púrpura–naranja')
const anomalyContrast = await legendContrast()
if (anomalyContrast.some((ratio) => ratio < 4.5)) {
  throw new Error(`contraste insuficiente en la leyenda de anomalía: ${anomalyContrast.join(', ')}`)
}
const anomalyLegend = await page.locator('.legend-steps').innerText()
if (!anomalyLegend.includes('▼') || !anomalyLegend.includes('▲')) {
  throw new Error('la leyenda de anomalía perdió los símbolos independientes del color')
}
expect(errors).toEqual([])

console.log(JSON.stringify({
  status: 'PASS',
  pngWidth,
  pngHeight,
  pngBytes: png.length,
  svgHeight,
  sequentialContrast: sequentialContrast.map((ratio) => Number(ratio.toFixed(2))),
  anomalyContrast: anomalyContrast.map((ratio) => Number(ratio.toFixed(2))),
}, null, 2))
await browser.close()
