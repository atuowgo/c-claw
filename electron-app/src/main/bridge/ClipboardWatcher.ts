import { clipboard } from 'electron'
import { EventEmitter } from 'events'

export interface ClipboardChange {
  content: string
  timestamp: number
}

export class ClipboardWatcher extends EventEmitter {
  private timer: ReturnType<typeof setInterval> | null = null
  private lastContent: string = ''
  private lastTimestamp: number = 0
  private running: boolean = false

  private static instance: ClipboardWatcher | null = null

  static getInstance(): ClipboardWatcher {
    if (!ClipboardWatcher.instance) {
      ClipboardWatcher.instance = new ClipboardWatcher()
    }
    return ClipboardWatcher.instance
  }

  private constructor() {
    super()
  }

  start(): void {
    if (this.running) return
    this.running = true
    // Capture initial state
    this.lastContent = clipboard.readText() || ''
    this.lastTimestamp = Date.now()
    this.timer = setInterval(() => {
      const current = clipboard.readText() || ''
      if (current !== this.lastContent) {
        this.lastContent = current
        this.lastTimestamp = Date.now()
        this.emit('change', {
          content: this.lastContent,
          timestamp: this.lastTimestamp
        })
      }
    }, 500)
  }

  stop(): void {
    this.running = false
    if (this.timer) {
      clearInterval(this.timer)
      this.timer = null
    }
  }

  getContent(): ClipboardChange {
    return {
      content: this.lastContent,
      timestamp: this.lastTimestamp
    }
  }

  writeContent(content: string): void {
    clipboard.writeText(content)
    this.lastContent = content
    this.lastTimestamp = Date.now()
  }
}