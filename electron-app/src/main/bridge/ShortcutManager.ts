import { globalShortcut } from 'electron'

interface ShortcutEntry {
  key: string
  action: string
}

class ShortcutManager {
  private shortcuts: Map<string, string> = new Map()
  private listeners: Array<(data: { key: string; action: string }) => void> = []

  onTrigger(callback: (data: { key: string; action: string }) => void): () => void {
    this.listeners.push(callback)
    return () => {
        const idx = this.listeners.indexOf(callback)
        if (idx >= 0) this.listeners.splice(idx, 1)
    }
  }

  private emit(data: { key: string; action: string }): void {
    this.listeners.forEach(cb => cb(data))
  }

  register(key: string, action: string): boolean {
    if (this.shortcuts.has(key)) {
      console.log(`[c-claw] Shortcut ${key} already registered, replacing`)
      this.unregister(key)
    }

    const success = globalShortcut.register(key, () => {
      console.log(`[c-claw] Shortcut triggered: ${key} -> ${action}`)
      this.emit({ key, action })
    })

    if (success) {
      this.shortcuts.set(key, action)
      console.log(`[c-claw] Shortcut registered: ${key} -> ${action}`)
    }

    return success
  }

  unregister(key: string): void {
    globalShortcut.unregister(key)
    this.shortcuts.delete(key)
  }

  unregisterAll(): void {
    globalShortcut.unregisterAll()
    this.shortcuts.clear()
  }

  isRegistered(key: string): boolean {
    return globalShortcut.isRegistered(key)
  }

  list(): ShortcutEntry[] {
    return Array.from(this.shortcuts.entries()).map(([key, action]) => ({ key, action }))
  }
}

export const shortcutManager = new ShortcutManager()