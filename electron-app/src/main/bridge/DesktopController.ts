import { screen, desktopCapturer, BrowserWindow } from 'electron'
import { exec } from 'child_process'
import * as os from 'os'
import * as path from 'path'
import * as fs from 'fs'

/**
 * DesktopController - screenshot and window management
 */
export class DesktopController {
  private screenshotDir: string

  constructor() {
    this.screenshotDir = path.join(os.homedir(), '.c-claw', 'screenshots')
    if (!fs.existsSync(this.screenshotDir)) {
      fs.mkdirSync(this.screenshotDir, { recursive: true })
    }
  }

  /**
   * Take a screenshot of the primary display and return base64 PNG.
   */
  async screenshot(): Promise<string> {
    const primaryDisplay = screen.getPrimaryDisplay()
    const sources = await desktopCapturer.getSources({
      types: ['screen'],
      thumbnailSize: primaryDisplay.size
    })

    if (sources.length === 0) {
      throw new Error('No screen sources found')
    }

    const source = sources[0]
    const dataUrl = source.thumbnail.toDataURL()
    // Extract base64 from data:image/png;base64,...
    const base64 = dataUrl.replace(/^data:image\/\w+;base64,/, '')

    // Also save to file
    const filename = `screenshot-${Date.now()}.png`
    const filePath = path.join(this.screenshotDir, filename)
    const buf = source.thumbnail.toPNG()
    fs.writeFileSync(filePath, buf)

    return JSON.stringify({
      format: 'png',
      width: primaryDisplay.size.width,
      height: primaryDisplay.size.height,
      file: filePath,
      // Truncate base64 to avoid huge responses (Claude vision handles ~5MB images)
      base64Length: base64.length
    })
  }

  /**
   * Save screenshot to a specific file path.
   */
  async screenshotToFile(filePath?: string): Promise<string> {
    const primaryDisplay = screen.getPrimaryDisplay()
    const sources = await desktopCapturer.getSources({
      types: ['screen'],
      thumbnailSize: primaryDisplay.size
    })

    if (sources.length === 0) {
      throw new Error('No screen sources found')
    }

    const dest = filePath || path.join(this.screenshotDir, `screenshot-${Date.now()}.png`)
    const buf = sources[0].thumbnail.toPNG()
    fs.writeFileSync(dest, buf)

    return JSON.stringify({
      file: dest,
      width: primaryDisplay.size.width,
      height: primaryDisplay.size.height,
      bytes: buf.length
    })
  }

  /**
   * List all open windows (cross-platform via platform commands).
   */
  async getWindowList(): Promise<string> {
    const platform = os.platform()
    let cmd: string

    if (platform === 'win32') {
      cmd = 'powershell -Command "Get-Process | Where-Object {$_.MainWindowTitle -ne \'\'} | Select-Object Id, ProcessName, MainWindowTitle | ConvertTo-Json -Compress"'
    } else if (platform === 'darwin') {
      cmd = `osascript -e 'tell application "System Events" to get {name, id} of every process whose background only is false'`
    } else {
      cmd = 'wmctrl -l 2>/dev/null || echo "[]"'
    }

    return new Promise((resolve, reject) => {
      exec(cmd, { timeout: 10000 }, (err, stdout, stderr) => {
        if (err && !stdout) {
          resolve(JSON.stringify({ error: 'Failed to list windows', detail: stderr || err.message }))
          return
        }
        try {
          if (platform === 'win32') {
            const data = JSON.parse(stdout)
            // Normalize: PowerShell returns a single object if only one result
            const items = Array.isArray(data) ? data : (data ? [data] : [])
            const windows = items.map((p: any) => ({
              id: p.Id,
              name: p.ProcessName,
              title: p.MainWindowTitle
            }))
            resolve(JSON.stringify(windows))
          } else if (platform === 'darwin') {
            // Parse AppleScript output
            const names = stdout.split(',').map((s: string) => s.trim().replace(/^name /, '').replace(/^id /, ''))
            resolve(JSON.stringify(names.map((n: string, i: number) => ({ id: i, name: n, title: n }))))
          } else {
            resolve(stdout || '[]')
          }
        } catch (e) {
          resolve(JSON.stringify({ raw: stdout }))
        }
      })
    })
  }

  /**
   * Focus a window by process name or title (platform-specific).
   */
  async focusWindow(idOrName: string): Promise<string> {
    const platform = os.platform()
    let cmd: string

    if (platform === 'win32') {
      // Try by process name first
      const psCmd = `powershell -Command "(Get-Process -Name '${idOrName}' -ErrorAction SilentlyContinue | Select-Object -First 1).Id"`
      return new Promise((resolve) => {
        exec(psCmd, { timeout: 5000 }, (err, stdout) => {
          const pid = stdout.trim()
          if (!pid) {
            resolve(JSON.stringify({ focused: false, reason: `Process not found: ${idOrName}` }))
            return
          }
          // Focus via PowerShell
          exec(`powershell -Command "$wshell = New-Object -ComObject wscript.shell; $wshell.AppActivate(${pid})"`,
            { timeout: 5000 }, () => {
              resolve(JSON.stringify({ focused: true, pid: parseInt(pid) }))
            })
        })
      })
    } else if (platform === 'darwin') {
      cmd = `osascript -e 'tell application "${idOrName}" to activate'`
      return new Promise((resolve) => {
        exec(cmd, { timeout: 5000 }, (err) => {
          resolve(JSON.stringify({ focused: !err, app: idOrName, error: err?.message }))
        })
      })
    } else {
      cmd = `wmctrl -a "${idOrName}" 2>/dev/null`
      return new Promise((resolve) => {
        exec(cmd, { timeout: 5000 }, (err) => {
          resolve(JSON.stringify({ focused: !err, target: idOrName, error: err?.message }))
        })
      })
    }
  }

  /**
   * Get screen info (dimensions, scale factor, cursor position).
   */
  getScreenInfo(): string {
    const cursor = screen.getCursorScreenPoint()
    const displays = screen.getAllDisplays().map(d => ({
      id: d.id,
      bounds: d.bounds,
      workArea: d.workArea,
      scaleFactor: d.scaleFactor,
      isPrimary: d.id === screen.getPrimaryDisplay().id
    }))

    return JSON.stringify({
      cursor,
      displayCount: displays.length,
      displays
    })
  }
}

// Singleton
let instance: DesktopController | null = null
export function getDesktopController(): DesktopController {
  if (!instance) {
    instance = new DesktopController()
  }
  return instance
}