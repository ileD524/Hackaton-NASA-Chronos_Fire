/**
 * Exportación PNG a partir del SVG del calendario.
 *
 * El SVG es la fuente de verdad: se rasteriza en un <canvas> a 2× para que el
 * texto y las celdas queden nítidos. Antes de rasterizar se embeben las fuentes
 * locales como data URIs, porque un SVG cargado como imagen no puede pedir
 * recursos externos. Si las fuentes no cargan, se degrada a la tipografía del
 * sistema sin romper la descarga.
 */
import { calendarExportFilename, downloadBlob } from './exportCalendarSvg'

const FONT_SOURCES = [
  { family: 'Fira Sans', weight: '700', url: '/fonts/FiraSans-Bold.ttf' },
  { family: 'Fira Sans', weight: '900', url: '/fonts/FiraSans-Black.ttf' },
  { family: 'Overpass', weight: '100 900', url: '/fonts/Overpass.ttf' },
] as const

let fontStylesheet: Promise<string> | undefined

function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  const chunk = 0x8000
  for (let index = 0; index < bytes.length; index += chunk) {
    binary += String.fromCharCode(...bytes.subarray(index, index + chunk))
  }
  return btoa(binary)
}

async function buildFontStylesheet(): Promise<string> {
  const faces = await Promise.all(
    FONT_SOURCES.map(async (font) => {
      try {
        const response = await fetch(font.url, { cache: 'force-cache' })
        if (!response.ok) return ''
        const base64 = arrayBufferToBase64(await response.arrayBuffer())
        return `@font-face{font-family:'${font.family}';font-style:normal;font-weight:${font.weight};src:url(data:font/ttf;base64,${base64}) format('truetype');font-display:block}`
      } catch {
        return ''
      }
    }),
  )
  return faces.filter(Boolean).join('')
}

async function embedFonts(svg: string): Promise<string> {
  fontStylesheet ??= buildFontStylesheet()
  const css = await fontStylesheet
  if (!css) return svg
  const style = `<style type="text/css"><![CDATA[${css}]]></style>`
  return svg.includes('</defs>')
    ? svg.replace('</defs>', `${style}</defs>`)
    : svg.replace(/<svg[^>]*>/, (tag) => `${tag}${style}`)
}

function svgSize(svg: string): { width: number; height: number } {
  const match = svg.match(/<svg[^>]*\bwidth="([\d.]+)"[^>]*\bheight="([\d.]+)"/)
  const width = Number(match?.[1])
  const height = Number(match?.[2])
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
    throw new Error('El SVG no tiene dimensiones válidas para generar el PNG.')
  }
  return { width, height }
}

/** Convierte el SVG del calendario en un PNG a `scale`× (por defecto 2×). */
export async function rasterizeCalendarSvg(svg: string, scale = 2): Promise<Blob> {
  const { width, height } = svgSize(svg)
  const withFonts = await embedFonts(svg)
  const url = URL.createObjectURL(new Blob([withFonts], { type: 'image/svg+xml;charset=utf-8' }))
  try {
    const image = new Image()
    await new Promise<void>((resolve, reject) => {
      image.onload = () => resolve()
      image.onerror = () => reject(new Error('No se pudo rasterizar el SVG del calendario.'))
      image.src = url
    })
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(width * scale)
    canvas.height = Math.round(height * scale)
    const context = canvas.getContext('2d')
    if (!context) throw new Error('El navegador no permite generar el PNG.')
    context.imageSmoothingEnabled = true
    context.imageSmoothingQuality = 'high'
    context.drawImage(image, 0, 0, canvas.width, canvas.height)
    return await new Promise<Blob>((resolve, reject) => {
      canvas.toBlob(
        (blob) => (blob ? resolve(blob) : reject(new Error('No se pudo generar el PNG.'))),
        'image/png',
      )
    })
  } finally {
    URL.revokeObjectURL(url)
  }
}

export async function downloadCalendarPng(svg: string, filename: string, scale = 2): Promise<void> {
  downloadBlob(await rasterizeCalendarSvg(svg, scale), filename)
}

export function calendarPngFilename(area: string, from: string, to: string): string {
  return calendarExportFilename(area, from, to, 'png')
}
