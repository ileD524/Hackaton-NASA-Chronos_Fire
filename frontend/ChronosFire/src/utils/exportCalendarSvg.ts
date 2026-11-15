/**
 * Exportación del calendario de actividad a SVG.
 *
 * El módulo no conoce los composables: recibe una instantánea ya resuelta
 * (valores formateados, colores y leyenda) para que la descarga coincida
 * exactamente con lo que se ve en pantalla. Así también queda listo para el
 * modo "hotspots" (F-02) sin cambiar el generador: alcanza con pasar otra
 * unidad y otros valores.
 */

export interface CalendarExportCell {
  /** Valor ya formateado; `null` = sin datos y se dibuja con trama. */
  value: string | null
  /** Color de fondo de la celda en pantalla. */
  fill: string
  /** Color de texto con contraste suficiente sobre `fill`. */
  ink: string
  /** Mes recortado por el rango consultado; se marca con asterisco. */
  partial?: boolean
  /** Diferencia extrema frente a la mediana; se marca con borde blanco. */
  extreme?: boolean
  /** Texto accesible de la celda (se usa como <title>). */
  label: string
}

export interface CalendarExportRow {
  year: number
  total: string
  cells: CalendarExportCell[]
}

export interface CalendarExportLegendItem {
  label: string
  fill: string
  ink: string
  mark?: string
}

export interface CalendarExportLegend {
  title: string
  items: CalendarExportLegendItem[]
  note?: string
}

export interface CalendarExportInput {
  title: string
  subtitle: string[]
  monthLabels: string[]
  rows: CalendarExportRow[]
  legend: CalendarExportLegend
  notes: string[]
  footer?: string
}

const WIDTH = 1120
const MARGIN = 40
const YEAR_WIDTH = 88
const CELL_GAP = 4
const CELL_HEIGHT = 40
const ROW_GAP = 4
const CONTENT_WIDTH = WIDTH - MARGIN * 2
const CELL_WIDTH = (CONTENT_WIDTH - YEAR_WIDTH - CELL_GAP * 12) / 12

const FONT_BODY = "'Overpass','Fira Sans',system-ui,sans-serif"
const FONT_DISPLAY = "'Fira Sans','Overpass',system-ui,sans-serif"
const PANEL = '#07173F'
const INK = '#FFFFFF'

const xml = (value: string) =>
  value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;')

/** Corta un texto en líneas de `maxChars` aproximados, respetando palabras. */
function wrap(text: string, maxChars: number): string[] {
  const lines: string[] = []
  let current = ''
  for (const word of text.split(/\s+/).filter(Boolean)) {
    if (!current) current = word
    else if (current.length + 1 + word.length <= maxChars) current += ` ${word}`
    else {
      lines.push(current)
      current = word
    }
  }
  if (current) lines.push(current)
  return lines
}

/** Legenda en filas: devuelve los elementos posicionados y el alto consumido. */
function layoutLegend(items: CalendarExportLegendItem[], top: number) {
  const placed: { item: CalendarExportLegendItem; x: number; y: number; width: number }[] = []
  const charWidth = 6.9
  const padding = 18
  let x = MARGIN
  let y = top
  for (const item of items) {
    const width = Math.min(CONTENT_WIDTH, Math.max(76, padding * 2 + (item.mark ? 14 : 0) + item.label.length * charWidth))
    if (x + width > MARGIN + CONTENT_WIDTH) {
      x = MARGIN
      y += 32
    }
    placed.push({ item, x, y, width })
    x += width + 4
  }
  return { placed, height: y - top + 24 }
}

/**
 * Construye el SVG completo. Es una función pura y devuelve texto listo para
 * descargar; no toca el DOM.
 */
export function buildCalendarSvg(input: CalendarExportInput): string {
  const subtitleLines = input.subtitle.flatMap((line) => wrap(line, 118))
  const legend = layoutLegend(input.legend.items, 0)
  const legendNoteLines = input.legend.note ? wrap(input.legend.note, 150) : []
  const noteLines = input.notes.flatMap((note) => wrap(note, 150))

  const titleBaseline = 54
  const subtitleBaseline = titleBaseline + 26
  const monthBaseline = subtitleBaseline + subtitleLines.length * 19 + 18
  const rowsTop = monthBaseline + 10
  const rowsHeight =
    input.rows.length === 0 ? 0 : input.rows.length * CELL_HEIGHT + (input.rows.length - 1) * ROW_GAP
  const legendTop = rowsTop + rowsHeight + (input.rows.length ? 34 : 8)
  const legendTitleBaseline = legendTop + 13
  const legendItemsTop = legendTitleBaseline + 13
  // Las líneas de texto se posicionan por línea base, no por borde superior.
  const legendNoteBaseline = legendItemsTop + legend.height + 16
  const notesBaseline =
    legendNoteBaseline + legendNoteLines.length * 17 + (legendNoteLines.length ? 8 : 0)
  const footerBaseline = notesBaseline + noteLines.length * 17 + 18
  const height = Math.ceil(footerBaseline + 22)

  const out: string[] = []
  out.push('<?xml version="1.0" encoding="UTF-8"?>')
  out.push(
    `<svg xmlns="http://www.w3.org/2000/svg" width="${WIDTH}" height="${height}" viewBox="0 0 ${WIDTH} ${height}" role="img" aria-labelledby="calendar-svg-title calendar-svg-desc">`,
  )
  out.push(`<title id="calendar-svg-title">${xml(`${input.title} — ${subtitleLines[0] ?? ''}`)}</title>`)
  out.push(
    `<desc id="calendar-svg-desc">${xml(
      `${subtitleLines.join('. ')}. ${input.notes.join(' ')}`,
    )}</desc>`,
  )
  out.push('<defs>')
  out.push(
    '<pattern id="calendar-svg-missing" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">',
  )
  out.push(`<rect width="6" height="6" fill="${PANEL}"/>`)
  out.push('<line x1="0" y1="0" x2="0" y2="6" stroke="#0042A6" stroke-width="1"/>')
  out.push('</pattern>')
  out.push('</defs>')

  out.push(`<rect width="${WIDTH}" height="${height}" fill="${PANEL}"/>`)

  // Encabezado
  out.push(
    `<text x="${MARGIN}" y="${titleBaseline}" font-family="${FONT_DISPLAY}" font-size="26" font-weight="900" letter-spacing="0.6" fill="${INK}">${xml(
      input.title,
    )}</text>`,
  )
  subtitleLines.forEach((line, index) => {
    out.push(
      `<text x="${MARGIN}" y="${subtitleBaseline + index * 19}" font-family="${FONT_BODY}" font-size="13" fill="${INK}" fill-opacity="0.86">${xml(
        line,
      )}</text>`,
    )
  })

  // Encabezado de meses
  input.monthLabels.forEach((label, month) => {
    const cx = MARGIN + YEAR_WIDTH + month * (CELL_WIDTH + CELL_GAP) + CELL_WIDTH / 2
    out.push(
      `<text x="${cx.toFixed(2)}" y="${monthBaseline}" text-anchor="middle" font-family="${FONT_BODY}" font-size="12" font-weight="700" fill="${INK}">${xml(
        label,
      )}</text>`,
    )
  })

  // Grilla año × mes
  input.rows.forEach((row, rowIndex) => {
    const top = rowsTop + rowIndex * (CELL_HEIGHT + ROW_GAP)
    const middle = top + CELL_HEIGHT / 2
    out.push(
      `<text x="${MARGIN}" y="${top + 17}" font-family="${FONT_DISPLAY}" font-size="14" font-weight="700" fill="${INK}">${xml(
        String(row.year),
      )}</text>`,
    )
    out.push(
      `<text x="${MARGIN}" y="${top + 31}" font-family="${FONT_BODY}" font-size="10" fill="${INK}" fill-opacity="0.8">${xml(
        row.total,
      )}</text>`,
    )
    row.cells.forEach((cell, month) => {
      const x = MARGIN + YEAR_WIDTH + month * (CELL_WIDTH + CELL_GAP)
      const cx = x + CELL_WIDTH / 2
      const missing = cell.value === null
      out.push('<g>')
      out.push(`<title>${xml(cell.label)}</title>`)
      out.push(
        `<rect class="calendar-cell-bg" x="${x.toFixed(2)}" y="${top}" width="${CELL_WIDTH.toFixed(2)}" height="${CELL_HEIGHT}" rx="4" fill="${
          missing ? 'url(#calendar-svg-missing)' : cell.fill
        }" stroke="#2E96F5" stroke-width="1"${missing ? ' stroke-dasharray="4 3"' : ''}/>`,
      )
      if (cell.extreme) {
        out.push(
          `<rect x="${x.toFixed(2)}" y="${top}" width="${CELL_WIDTH.toFixed(2)}" height="${CELL_HEIGHT}" rx="4" fill="none" stroke="${INK}" stroke-width="2"/>`,
        )
      }
      const star = cell.partial && !missing ? '<tspan dy="-3" font-size="8">*</tspan>' : ''
      out.push(
        `<text class="calendar-cell-value" x="${cx.toFixed(2)}" y="${middle}" dy="0.35em" text-anchor="middle" font-family="${FONT_BODY}" font-size="11" font-weight="700" fill="${xml(
          cell.ink,
        )}">${xml(cell.value ?? '—')}${star}</text>`,
      )
      out.push('</g>')
    })
  })

  // Leyenda
  out.push(
    `<text x="${MARGIN}" y="${legendTitleBaseline}" font-family="${FONT_BODY}" font-size="12" font-weight="700" fill="${INK}">${xml(
      input.legend.title,
    )}</text>`,
  )
  for (const { item, x, y, width } of legend.placed) {
    const top = legendItemsTop + (y - 0)
    out.push(
      `<rect class="calendar-legend-item" x="${x.toFixed(2)}" y="${top}" width="${width.toFixed(2)}" height="24" rx="3" fill="${item.fill}" stroke="#2E96F5" stroke-width="1"/>`,
    )
    out.push(
      `<text x="${(x + width / 2).toFixed(2)}" y="${top + 12}" dy="0.35em" text-anchor="middle" font-family="${FONT_BODY}" font-size="11" font-weight="700" fill="${xml(
        item.ink,
      )}">${xml((item.mark ? `${item.mark} ` : '') + item.label)}</text>`,
    )
  }
  legendNoteLines.forEach((line, index) => {
    out.push(
      `<text x="${MARGIN}" y="${legendNoteBaseline + index * 17}" font-family="${FONT_BODY}" font-size="11" fill="${INK}" fill-opacity="0.84">${xml(
        line,
      )}</text>`,
    )
  })

  // Nota metodológica
  noteLines.forEach((line, index) => {
    out.push(
      `<text x="${MARGIN}" y="${notesBaseline + index * 17}" font-family="${FONT_BODY}" font-size="11" fill="${INK}" fill-opacity="0.9">${xml(
        line,
      )}</text>`,
    )
  })

  if (input.footer) {
    out.push(
      `<text x="${MARGIN}" y="${footerBaseline}" font-family="${FONT_BODY}" font-size="10" fill="${INK}" fill-opacity="0.68">${xml(
        input.footer,
      )}</text>`,
    )
  }

  out.push('</svg>')
  return out.join('\n')
}

/** Nombre de archivo estable y sin acentos: chronos-fire-calendario-<área>-<desde>-<hasta>.svg */
export function calendarSvgFilename(area: string, from: string, to: string): string {
  const slug =
    area
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 48)
      .replace(/-+$/g, '') || 'area'
  return `chronos-fire-calendario-${slug}-${from}-${to}.svg`
}

/**
 * Descarga el SVG con un Blob y un enlace temporal. Debe llamarse desde una
 * acción del usuario (click) para que el navegador permita la descarga.
 */
export function downloadCalendarSvg(svg: string, filename: string): void {
  const blob = new Blob([svg], { type: 'image/svg+xml;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
  // Dar tiempo al navegador a iniciar la descarga antes de liberar la URL.
  setTimeout(() => URL.revokeObjectURL(url), 2000)
}
