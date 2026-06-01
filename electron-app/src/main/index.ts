import { app, BrowserWindow, globalShortcut, ipcMain } from 'electron'
import { join } from 'path'
import { createTray } from './tray'
import { JavaProcess } from './java-launcher'
import { start as startBridge, stop as stopBridge, bridgePort } from './bridge'

let mainWindow: BrowserWindow | null = null
let javaProcess: JavaProcess | null = null

function createWindow(): void {
  mainWindow = new BrowserWindow({
    width: 800,
    height: 600,
    center: true,
    resizable: true,
    frame: true,
    show: false,
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  })

  mainWindow.on('close', (event) => {
    if (mainWindow) {
      event.preventDefault()
      mainWindow.hide()
    }
  })

  mainWindow.on('ready-to-show', () => {
    mainWindow?.show()
  })

  // Load renderer URL from electron-vite
  if (process.env.ELECTRON_RENDERER_URL) {
    mainWindow.loadURL(process.env.ELECTRON_RENDERER_URL)
  } else {
    mainWindow.loadFile(join(__dirname, '../renderer/index.html'))
  }
}

app.whenReady().then(async () => {
  // Register IPC handlers
  ipcMain.handle('get-backend-port', () => javaProcess?.getPort() ?? null)
  ipcMain.handle('get-bridge-port', () => bridgePort)

  // Start Java backend before showing the window
  javaProcess = new JavaProcess()
  try {
    await javaProcess.start()
  } catch (err) {
    console.error('[c-claw] Failed to start Java backend:', err)
    // Continue anyway — app can show an error state
  }

  // Start Bridge HTTP server
  try {
    await startBridge()
  } catch (err) {
    console.error('[c-claw] Failed to start Bridge server:', err)
  }

  createWindow()
  createTray(mainWindow!)

  // Register Alt+Space global shortcut to toggle window
  const altSpaceRegistered = globalShortcut.register('Alt+Space', () => {
    if (mainWindow) {
      if (mainWindow.isVisible()) {
        mainWindow.hide()
      } else {
        mainWindow.show()
        mainWindow.focus()
      }
    }
  })
  if (altSpaceRegistered) {
    console.log('[c-claw] Global shortcut registered: Alt+Space')
  } else {
    console.warn('[c-claw] Failed to register Alt+Space (may be taken by another app)')
  }

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow()
    } else {
      mainWindow?.show()
    }
  })
})

app.on('window-all-closed', () => {
  // Don't quit on window close — app stays in tray
})

app.on('before-quit', () => {
  globalShortcut.unregisterAll()
  // Stop Java backend on quit
  if (javaProcess) {
    javaProcess.stop()
  }
  stopBridge()
})