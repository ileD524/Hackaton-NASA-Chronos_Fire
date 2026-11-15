import { chromium } from '@playwright/test'
import fs from 'node:fs'

const BASE_URL = process.env.BASE_URL || 'http://127.0.0.1:5173/'
const browser = await chromium.launch({
  executablePath: process.env.CHROMIUM_PATH || undefined,
  headless: true,
  args: ['--no-sandbox'],
})
const page = await browser.newPage({ viewport: { width: 1440, height: 1050 } })
const errors = []
page.on('pageerror', (error) => errors.push(error.message))
page.on('console', (message) => { if (message.type() === 'error') errors.push(message.text()) })
await page.goto(BASE_URL, { waitUntil: 'networkidle' })
await page.locator('.cal-cell:not(:disabled)').first().waitFor()
fs.mkdirSync('artifacts', { recursive: true })
await page.screenshot({ path: 'artifacts/desktop.png', fullPage: true })
console.log(JSON.stringify({
  title: await page.title(),
  errors,
  calendarButtons: await page.locator('.cal-cell').count(),
  kpis: await page.locator('.kpi-list').innerText(),
  map: await page.locator('.map-toolbar').innerText(),
  alerts: await page.locator('#alertas').innerText(),
  validation: await page.locator('#validacion').innerText(),
  overflow: await page.evaluate(() => document.documentElement.scrollWidth > innerWidth),
}))
await browser.close()
