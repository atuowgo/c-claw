import { Browser, Page, chromium } from 'playwright'

export class BrowserController {
  private browser: Browser | null = null
  private page: Page | null = null

  private async ensureBrowser(): Promise<{ browser: Browser; page: Page }> {
    if (!this.browser || !this.page) {
      this.browser = await chromium.launch({ headless: true })
      this.page = await this.browser.newPage()
    }
    return { browser: this.browser, page: this.page }
  }

  async navigate(url: string): Promise<string> {
    try {
      const { page } = await this.ensureBrowser()
      await page.goto(url, { waitUntil: 'domcontentloaded' })
      const title = await page.title()
      return JSON.stringify({ url, title })
    } catch (e: any) {
      return JSON.stringify({ error: 'navigate failed', detail: e.message })
    }
  }

  async getContent(): Promise<string> {
    try {
      const { page } = await this.ensureBrowser()
      const content = await page.content()
      return content
    } catch (e: any) {
      return JSON.stringify({ error: 'getContent failed', detail: e.message })
    }
  }

  async click(selector: string): Promise<string> {
    try {
      const { page } = await this.ensureBrowser()
      await page.click(selector)
      return JSON.stringify({ clicked: true, selector })
    } catch (e: any) {
      return JSON.stringify({ error: 'click failed', detail: e.message, selector })
    }
  }

  async type(selector: string, text: string): Promise<string> {
    try {
      const { page } = await this.ensureBrowser()
      await page.fill(selector, text)
      return JSON.stringify({ typed: true, selector, text })
    } catch (e: any) {
      return JSON.stringify({ error: 'type failed', detail: e.message, selector })
    }
  }

  async screenshot(): Promise<string> {
    try {
      const { page } = await this.ensureBrowser()
      const buf = await page.screenshot({ type: 'png', fullPage: true })
      const base64 = buf.toString('base64')
      const viewport = page.viewportSize()
      return JSON.stringify({
        format: 'png',
        base64,
        base64Length: base64.length,
        width: viewport?.width,
        height: viewport?.height
      })
    } catch (e: any) {
      return JSON.stringify({ error: 'screenshot failed', detail: e.message })
    }
  }

  async execute(js: string): Promise<string> {
    try {
      const { page } = await this.ensureBrowser()
      const result = await page.evaluate(js)
      return JSON.stringify({ result })
    } catch (e: any) {
      return JSON.stringify({ error: 'execute failed', detail: e.message })
    }
  }

  async close(): Promise<void> {
    try {
      if (this.browser) {
        await this.browser.close()
        this.browser = null
        this.page = null
      }
    } catch {
      this.browser = null
      this.page = null
    }
  }
}

let instance: BrowserController | null = null

export function getBrowserController(): BrowserController {
  if (!instance) {
    instance = new BrowserController()
  }
  return instance
}