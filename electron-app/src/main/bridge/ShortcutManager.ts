import { globalShortcut } from 'electron'

interface ShortcutEntry {
  key: string
  action: string
}

class ShortcutManager {
  private shortcuts: Map<string, string> = new Map()
  private listeners: Array<(data: { key: string; action: string }) => void> = []

  onTrigger(callback: (data: { key: string; action: string }) => void): void {
    this.listeners.push(callback)
  }

  private emit(event: string, data: { key: string; action: string }): void {
    if (event === 'trigger') {
      this.listeners.forEach(cb => cb(data))
    }
  }

  register(key: string, action: string): boolean {
    if (this.shortcuts.has(key)) {
      console.log(`[c-claw] Shortcut ${key} already registered, replacing`)
      this.unregister(key)
    }

    const success = globalShortcut.register(key, () => {
      console.log(`[c-claw] Shortcut triggered: ${key} -> ${action}`)
      this.emit('trigger', { key, action })
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