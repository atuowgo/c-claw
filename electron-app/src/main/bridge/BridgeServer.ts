import express, { NextFunction, Request, Response } from 'express'
import { Server } from 'http'
import { getActiveWindow } from './WindowWatcher'
import { ClipboardWatcher } from './ClipboardWatcher'
import { shortcutManager } from './ShortcutManager'
import * as fs from 'fs'
import * as path from 'path'
import * as os from 'os'
import { getDesktopController } from './DesktopController'
import { readFile, writeFile, listDir, searchFiles, fileInfo } from './FileSystemController'
import { getBrowserController } from './BrowserController'

export let bridgePort: number | null = null

const app = express()
let server: Server | null = null
const clipboardWatcher = ClipboardWatcher.getInstance()

// CORS: allow local origins
app.use((_req: Request, res: Response, next: NextFunction) => {
  const origin = _req.headers.origin || ''
  if (!origin || origin.startsWith('http://localhost') || origin.startsWith('http://127.0.0.1')) {
    res.setHeader('Access-Control-Allow-Origin', origin || '*')
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type')
  }
  if (_req.method === 'OPTIONS') {
    res.sendStatus(204)
    return
  }
  next()
})

app.use(express.json())

app.get('/bridge/window/active', async (_req: Request, res: Response) => {
  const window = await getActiveWindow()
  res.json(window)
})

app.get('/bridge/clipboard', (_req: Request, res: Response) => {
  res.json(clipboardWatcher.getContent())
})

app.post('/bridge/clipboard', (req: Request, res: Response) => {
  const { content } = req.body
  if (typeof content !== 'string') {
    res.status(400).json({ error: 'content must be a string' })
    return
  }
  clipboardWatcher.writeContent(content)
  res.json({ success: true })
})

app.post('/bridge/shortcut', (req: Request, res: Response) => {
  const { key, action } = req.body
  if (typeof key !== 'string' || typeof action !== 'string') {
    res.status(400).json({ error: 'key and action must be strings' })
    return
  }
  const success = shortcutManager.register(key, action)
  res.json({ registered: success, key, action })
})

app.delete('/bridge/shortcut/:key', (req: Request, res: Response) => {
  const { key } = req.params
  shortcutManager.unregister(key)
  res.json({ unregistered: true, key })
})

app.get('/bridge/shortcuts', (_req: Request, res: Response) => {
  res.json(shortcutManager.list())
})

app.get('/bridge/clipboard/events', (_req: Request, res: Response) => {
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive'
  })

  const onClipboardChange = (data: { content: string; timestamp: number }) => {
    res.write(`event: change\ndata: ${JSON.stringify(data)}\n\n`)
  }

  clipboardWatcher.on('change', onClipboardChange)

  res.on('close', () => {
    clipboardWatcher.off('change', onClipboardChange)
  })
})

// ---- Desktop routes ----

app.get('/bridge/desktop/screenshot', async (_req: Request, res: Response) => {
  try {
    const result = await getDesktopController().screenshot()
    res.json(JSON.parse(result))
  } catch (e: any) {
    res.status(500).json({ error: e.message || 'screenshot failed' })
  }
})

app.get('/bridge/desktop/screenshot-file', async (req: Request, res: Response) => {
  try {
    const result = await getDesktopController().screenshotToFile(req.query.filePath as string | undefined)
    res.json(JSON.parse(result))
  } catch (e: any) {
    res.status(500).json({ error: e.message || 'screenshotToFile failed' })
  }
})

app.get('/bridge/desktop/windows', async (_req: Request, res: Response) => {
  try {
    const result = await getDesktopController().getWindowList()
    res.json(JSON.parse(result))
  } catch (e: any) {
    res.status(500).json({ error: e.message || 'getWindowList failed' })
  }
})

app.post('/bridge/desktop/focus', async (req: Request, res: Response) => {
  try {
    const { idOrName } = req.body
    if (typeof idOrName !== 'string') {
      res.status(400).json({ error: 'idOrName must be a string' })
      return
    }
    const result = await getDesktopController().focusWindow(idOrName)
    res.json(JSON.parse(result))
  } catch (e: any) {
    res.status(500).json({ error: e.message || 'focusWindow failed' })
  }
})

app.get('/bridge/desktop/screen-info', (_req: Request, res: Response) => {
  try {
    const result = getDesktopController().getScreenInfo()
    res.json(JSON.parse(result))
  } catch (e: any) {
    res.status(500).json({ error: e.message || 'getScreenInfo failed' })
  }
})

// ---- FileSystem routes ----

app.get('/bridge/fs/read', (req: Request, res: Response) => {
  try {
    const { path: filePath, maxBytes } = req.query
    if (typeof filePath !== 'string') {
      res.status(400).json({ error: 'path query param is required' })
      return
    }
    const maxBytesNum = maxBytes ? parseInt(maxBytes as string, 10) : undefined
    const content = readFile(filePath, maxBytesNum)
    res.json({ content, path: filePath })
  } catch (e: any) {
    res.status(500).json({ error: e.message || 'readFile failed' })
  }
})

app.post('/bridge/fs/write', (req: Request, res: Response) => {
  try {
    const { path: filePath, content } = req.body
    if (typeof filePath !== 'string' || typeof content !== 'string') {
      res.status(400).json({ error: 'path and content are required' })
      return
    }
    const result = writeFile(filePath, content)
    res.json(JSON.parse(result))
  } catch (e: any) {
    res.status(500).json({ error: e.message || 'writeFile failed' })
  }
})

app.get('/bridge/fs/list', (req: Request, res: Response) => {
  try {
    const { path: dirPath } = req.query
    if (typeof dirPath !== 'string') {
      res.status(400).json({ error: 'path query param is required' })
      return
    }
    const result = listDir(dirPath)
    res.json(JSON.parse(result))
  } catch (e: any) {
    res.status(500).json({ error: e.message || 'listDir failed' })
  }
})

app.get('/bridge/fs/search', (req: Request, res: Response) => {
  try {
    const { path: dirPath, pattern } = req.query
    if (typeof dirPath !== 'string' || typeof pattern !== 'string') {
      res.status(400).json({ error: 'path and pattern query params are required' })
      return
    }
    const result = searchFiles(dirPath, pattern)
    res.json(JSON.parse(result))
  } catch (e: any) {
    res.status(500).json({ error: e.message || 'searchFiles failed' })
  }
})

app.get('/bridge/fs/info', (req: Request, res: Response) => {
  try {
    const { path: filePath } = req.query
    if (typeof filePath !== 'string') {
      res.status(400).json({ error: 'path query param is required' })
      return
    }
    const result = fileInfo(filePath)
    res.json(JSON.parse(result))
  } catch (e: any) {
    res.status(500).json({ error: e.message || 'fileInfo failed' })
  }
})

// ---- Browser routes ----

app.post('/bridge/browser/navigate', async (req: Request, res: Response) => {
  try {
    const { url } = req.body
    if (typeof url !== 'string') {
      res.status(400).json({ error: 'url is required' })
      return
    }
    const result = await getBrowserController().navigate(url)
    res.json(JSON.parse(result))
  } catch (e: any) {
    res.status(500).json({ error: e.message || 'navigate failed' })
  }
})

app.get('/bridge/browser/content', async (_req: Request, res: Response) => {
  try {
    const result = await getBrowserController().getContent()
    res.json(JSON.parse(result))
  } catch (e: any) {
    res.status(500).json({ error: e.message || 'getContent failed' })
  }
})

app.post('/bridge/browser/click', async (req: Request, res: Response) => {
  try {
    const { selector } = req.body
    if (typeof selector !== 'string') {
      res.status(400).json({ error: 'selector is required' })
      return
    }
    const result = await getBrowserController().click(selector)
    res.json(JSON.parse(result))
  } catch (e: any) {
    res.status(500).json({ error: e.message || 'click failed' })
  }
})

app.post('/bridge/browser/type', async (req: Request, res: Response) => {
  try {
    const { selector, text } = req.body
    if (typeof selector !== 'string' || typeof text !== 'string') {
      res.status(400).json({ error: 'selector and text are required' })
      return
    }
    const result = await getBrowserController().type(selector, text)
    res.json(JSON.parse(result))
  } catch (e: any) {
    res.status(500).json({ error: e.message || 'type failed' })
  }
})

app.get('/bridge/browser/screenshot', async (_req: Request, res: Response) => {
  try {
    const result = await getBrowserController().screenshot()
    res.json(JSON.parse(result))
  } catch (e: any) {
    res.status(500).json({ error: e.message || 'screenshot failed' })
  }
})

app.post('/bridge/browser/execute', async (req: Request, res: Response) => {
  try {
    const { js } = req.body
    if (typeof js !== 'string') {
      res.status(400).json({ error: 'js is required' })
      return
    }
    const result = await getBrowserController().execute(js)
    res.json(JSON.parse(result))
  } catch (e: any) {
    res.status(500).json({ error: e.message || 'execute failed' })
  }
})

export function start(): Promise<number> {
  return new Promise((resolve, reject) => {
    if (server) {
      reject(new Error('BridgeServer already running'))
      return
    }

    server = app.listen(0, '127.0.0.1', () => {
      const addr = server!.address()
      if (!addr || typeof addr === 'string') {
        reject(new Error('Failed to get server address'))
        return
      }
      bridgePort = addr.port

      // Write port to ~/.c-claw/bridge.port
      const configDir = path.join(os.homedir(), '.c-claw')
      if (!fs.existsSync(configDir)) {
        fs.mkdirSync(configDir, { recursive: true })
      }
      fs.writeFileSync(path.join(configDir, 'bridge.port'), String(bridgePort))

      // Start clipboard polling
      clipboardWatcher.start()

      console.log(`[c-claw] Bridge HTTP server started on port ${bridgePort}`)
      resolve(bridgePort)
    })

    server.on('error', (err) => {
      reject(err)
    })
  })
}

export function stop(): Promise<void> {
  return new Promise((resolve) => {
    clipboardWatcher.stop()
    shortcutManager.unregisterAll()
    if (server) {
      server.close(() => {
        server = null
        bridgePort = null
        // Clean up port file
        const configDir = path.join(os.homedir(), '.c-claw')
        const portFile = path.join(configDir, 'bridge.port')
        try {
            if (fs.existsSync(portFile)) {
                fs.unlinkSync(portFile)
            }
        } catch (e) {
            console.error('[c-claw] Failed to remove bridge.port:', e)
        }
        console.log('[c-claw] Bridge HTTP server stopped')
        resolve()
      })
    } else {
      resolve()
    }
  })
}