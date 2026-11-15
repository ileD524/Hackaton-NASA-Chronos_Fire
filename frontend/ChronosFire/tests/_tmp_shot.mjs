import { chromium } from '@playwright/test'
import fs from 'node:fs'
const OUT = '../../tmp-shots'
fs.mkdirSync(OUT, { recursive: true })
const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] })
for (const [name, w, h] of [['wide', 1920, 1080], ['laptop', 1440, 900], ['common', 1366, 768], ['small', 1024, 768], ['mobile', 420, 900]]) {
  const page = await browser.newPage({ viewport: { width: w, height: h }, locale: 'es-AR' })
  await page.goto('http://127.0.0.1:5173/', { waitUntil: 'domcontentloaded' })
  await page.locator('.cal-cell:not(:disabled)').first().waitFor({ timeout: 60000 })
  await page.getByRole('button', { name: 'Ampliar mapa' }).click()
  await page.waitForTimeout(900)
  const panel = page.locator('.map-panel.is-expanded')
  const closed = await panel.evaluate((el) => ({ scroll: el.scrollHeight, client: el.clientHeight, w: Math.round(el.getBoundingClientRect().width), map: Math.round(el.querySelector('.map').getBoundingClientRect().width) }))
  await page.screenshot({ path: `${OUT}/${name}.png` })
  await page.locator('.map-howto summary').click()
  await page.waitForTimeout(500)
  const open = await panel.evaluate((el) => ({ scroll: el.scrollHeight, client: el.clientHeight, map: Math.round(el.querySelector('.map').getBoundingClientRect().width) }))
  await page.screenshot({ path: `${OUT}/${name}-open.png` })
  console.log(name, 'closed', JSON.stringify(closed), 'open', JSON.stringify(open))
  await page.close()
}
await browser.close()
