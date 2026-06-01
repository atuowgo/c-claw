import express, { NextFunction, Request, Response } from 'express'
import { Server } from 'http'
import { getActiveWindow } from './WindowWatcher'
import { ClipboardWatcher } from './ClipboardWatcher'
import { shortcutManager } from './ShortcutManager'
import * as fs from 'fs'
import * as path from 'path'
import * as os from 'os'

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