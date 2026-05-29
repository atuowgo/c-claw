import { clipboard } from 'electron'
import { EventEmitter } from 'events'

export interface ClipboardChange {
  content: string
  timestamp: number
}

let instance: ClipboardWatcher | null = null

export class ClipboardWatcher extends EventEmitter {
  private timer: ReturnType<typeof setInterval> | null = null
  private lastContent: string = ''
  private lastTimestamp: number = 0
  private running: boolean = false

  constructor() {
    super()
    if (instance) return instance
    instance = this
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
    this.lastContent = content
    this.lastTimestamp = Date.now()
    clipboard.writeText(content)
  }
}