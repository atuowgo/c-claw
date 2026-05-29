import { spawn, ChildProcess } from 'child_process'
import * as path from 'path'
import * as fs from 'fs'
import * as os from 'os'
import { findJava, findJar } from './jar-finder'

export class JavaProcess {
  private process: ChildProcess | null = null
  private port: number | null = null
  private readonly portFilePath: string

  constructor() {
    this.portFilePath = path.join(os.homedir(), '.c-claw', 'port')
  }

  getPort(): number | null {
    return this.port
  }

  /**
   * Start Java backend.
   * Returns the port number once the backend is healthy.
   */
  async start(): Promise<number> {
    const javaPath = findJava()
    const jarPath = findJar()

    console.log(`[c-claw] Starting Java backend: ${javaPath} -jar ${jarPath}`)

    return new Promise((resolve, reject) => {
      this.process = spawn(javaPath, ['-jar', jarPath], {
        env: { ...process.env },
        stdio: ['ignore', 'pipe', 'pipe']
      })

      let startupTimeout: NodeJS.Timeout

      this.process.stdout?.on('data', (data: Buffer) => {
        console.log(`[java] ${data.toString().trim()}`)
      })

      this.process.stderr?.on('data', (data: Buffer) => {
        console.error(`[java:err] ${data.toString().trim()}`)
      })

      this.process.on('error', (err) => {
        console.error('[c-claw] Failed to start Java process:', err)
        clearTimeout(startupTimeout)
        reject(err)
      })

      this.process.on('exit', (code, signal) => {
        console.log(`[c-claw] Java process exited: code=${code}, signal=${signal}`)
        clearTimeout(startupTimeout)
        this.process = null
        this.port = null
      })

      // Wait for port file and health check
      this.waitForReady()
        .then(port => {
          clearTimeout(startupTimeout)
          this.port = port
          resolve(port)
        })
        .catch(err => {
          clearTimeout(startupTimeout)
          reject(err)
        })

      // Safety timeout: 30 seconds
      startupTimeout = setTimeout(() => {
        reject(new Error('Java backend startup timed out (30s)'))
      }, 30000)
    })
  }

  /**
   * Poll for port file, then health check endpoint.
   */
  private async waitForReady(): Promise<number> {
    // Step 1: Wait for port file (max 20 seconds)
    const port = await this.waitForPortFile(20000)

    // Step 2: Wait for health endpoint (max 10 seconds)
    await this.waitForHealth(port, 10000)

    return port
  }

  private async waitForPortFile(timeoutMs: number): Promise<number> {
    const start = Date.now()
    while (Date.now() - start < timeoutMs) {
      try {
        if (fs.existsSync(this.portFilePath)) {
          const content = fs.readFileSync(this.portFilePath, 'utf-8').trim()
          const port = parseInt(content, 10)
          if (!isNaN(port)) {
            console.log(`[c-claw] Found port file: ${port}`)
            return port
          }
        }
      } catch {
        // File might be mid-write, retry
      }
      await this.sleep(500)
    }
    throw new Error(`Port file not found at ${this.portFilePath} within ${timeoutMs}ms`)
  }

  private async waitForHealth(port: number, timeoutMs: number): Promise<void> {
    const url = `http://127.0.0.1:${port}/api/health`
    const start = Date.now()

    while (Date.now() - start < timeoutMs) {
      try {
        const response = await fetch(url)
        if (response.ok) {
          console.log(`[c-claw] Backend health check passed`)
          return
        }
      } catch {
        // Connection refused, retry
      }
      await this.sleep(500)
    }
    throw new Error(`Health check failed for ${url} within ${timeoutMs}ms`)
  }

  /**
   * Check if backend is still healthy.
   */
  async healthCheck(): Promise<boolean> {
    if (!this.port) return false
    try {
      const response = await fetch(`http://127.0.0.1:${this.port}/api/health`)
      return response.ok
    } catch {
      return false
    }
  }

  /**
   * Gracefully stop the Java backend.
   * Uses SIGTERM on Unix, which Node maps to TerminateProcess on Windows.
   */
  async stop(): Promise<void> {
    const proc = this.process
    if (!proc) return

    console.log('[c-claw] Stopping Java backend...')

    return new Promise(resolve => {
      const forceKillTimeout = setTimeout(() => {
        console.log('[c-claw] Force killing Java process')
        try { proc.kill('SIGKILL') } catch {}
        resolve()
      }, 10000) // 10s fallback

      proc.on('exit', () => {
        clearTimeout(forceKillTimeout)
        console.log('[c-claw] Java backend stopped gracefully')
        resolve()
      })

      // On Windows, Node maps 'SIGTERM' to TerminateProcess
      try { proc.kill('SIGTERM') } catch {}
    })
  }

  private sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms))
  }
}