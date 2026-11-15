import { chromium } from '@playwright/test'
const browser=await chromium.launch({executablePath:'/usr/bin/chromium',headless:true,args:['--no-sandbox']})
const page=await browser.newPage({viewport:{width:1440,height:1050}})
const errors=[]
page.on('pageerror',e=>errors.push(e.message))
page.on('console',m=>{if(m.type()==='error')errors.push(m.text())})
await page.goto('http://127.0.0.1:8765/',{waitUntil:'networkidle'})
await page.locator('.cal-cell:not(:disabled)').first().waitFor()
await page.screenshot({path:'artifacts/desktop.png',fullPage:true})
console.log(JSON.stringify({title:await page.title(),errors,calendarButtons:await page.locator('.cal-cell').count(),kpis:await page.locator('.kpi-list').innerText(),map:await page.locator('.map-toolbar').innerText(),overflow:await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth)}))
await browser.close()
