# Phase 1 System Bridge 实现计划

**日期**：2026-05-29
**状态**：待执行
**依赖**：Phase 0（已完成）

---

## 一、目标

在 Electron 主进程中建立 System Bridge HTTP 服务，提供窗口监控和剪贴板读写能力，供 Java 后端通过 HTTP 调用。

---

## 二、当前代码库分析

### 2.1 现有结构

```
electron-app/src/
├── main/
│   ├── index.ts              # 入口：创建窗口、启动Java、托盘
│   ├── tray.ts               # 系统托盘
│   └── java-launcher/
│       ├── index.ts          # re-export
│       ├── JavaProcess.ts    # Java进程管理（spawn、健康检查、端口文件）
│       └── jar-finder.ts     # 查找java和jar路径
├── preload/
│   └── index.ts              # contextBridge：暴露 getBackendPort
└── renderer/
    ├── index.html / main.ts / App.vue
    ├── api/chat.ts           # SSE解析工具
    ├── stores/chat.ts        # Pinia store：消息管理、SSE流式读取
    └── components/           # ChatWindow, InputBox, MessageList
```

### 2.2 关键技术点

| 项目 | 详情 |
|------|------|
| 构建工具 | electron-vite（CommonJS 模式） |
| 主进程入口 | `src/main/index.ts` |
| Java 端口发现 | Java 后端写入 `~/.c-claw/port`，JavaProcess 轮询读取 |
| IPC 通信 | preload 通过 `contextBridge` 暴露 `getBackendPort` |
| 渲染进程->后端 | 直接 HTTP fetch 到 `http://127.0.0.1:{port}` |
| 开发/生产 jar | 开发模式从 `../java-backend/target/` 查找；生产模式从 `process.resourcesPath` |

### 2.3 现有端口文件机制

`JavaProcess.ts` 已经使用了 `~/.c-claw/port` 文件作为 Java 后端端口发现机制。Bridge Server 需要类似的机制，使用 `~/.c-claw/bridge.port`。

---

## 三、架构设计

### 3.1 整体流程

```
┌─ Electron Main Process ─────────────────────────────────────────┐
│                                                                 │
│  app.whenReady()                                                │
│  ├── new JavaProcess()    → 启动 Java 后端                      │
│  ├── new BridgeServer()   → 启动 System Bridge HTTP 服务         │
│  │   ├── WindowWatcher    → GET /bridge/window/active           │
│  │   └── ClipboardWatcher → GET/POST /bridge/clipboard          │
│  │                        → SSE  /bridge/clipboard/events       │
│  ├── createWindow()       → BrowserWindow                       │
│  └── createTray()         → 系统托盘                            │
│                                                                 │
│  BridgeServer 监听 127.0.0.1:{随机端口}                          │
│  端口写入 ~/.c-claw/bridge.port                                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
         │
         │ HTTP (127.0.0.1 only)
         ▼
┌─ Java Backend ──────────────────────────────────────────────────┐
│  Spring Boot 读取 ~/.c-claw/bridge.port                          │
│  调用 http://127.0.0.1:{bridgePort}/bridge/*                    │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 端口发现双文件机制

| 文件 | 用途 | 写入方 | 读取方 |
|------|------|--------|--------|
| `~/.c-claw/port` | Java 后端端口（已有） | Java 后端 | Electron、渲染进程 |
| `~/.c-claw/bridge.port` | Bridge 服务端口（新增） | Electron 主进程 | Java 后端 |

两者互不干扰，Bridge 端口文件在 BridgeServer 成功监听后立即写入。

---

## 四、文件变更清单

### 4.1 新增文件

| 文件路径 | 用途 |
|----------|------|
| `src/main/bridge/BridgeServer.ts` | HTTP 服务核心：Express 路由注册、端口管理、优雅关闭 |
| `src/main/bridge/WindowWatcher.ts` | 活动窗口监控：调用 active-win 获取前台窗口信息 |
| `src/main/bridge/ClipboardWatcher.ts` | 剪贴板监控：轮询 clipboard API、SSE 推送变更事件 |
| `src/main/bridge/index.ts` | re-export 入口 |

### 4.2 修改文件

| 文件路径 | 变更内容 |
|----------|----------|
| `src/main/index.ts` | 导入 BridgeServer，在 JavaProcess 启动后启动 Bridge；在 before-quit 时关闭 Bridge |
| `src/preload/index.ts` | 暴露 `getBridgePort` 方法（可选，方便渲染进程调试） |
| `src/renderer/env.d.ts` | 添加 `getBridgePort` 类型声明 |
| `package.json` | 添加依赖：`express`、`active-win`；添加 `@types/express` 到 devDependencies |

---

## 五、模块详细设计

### 5.1 BridgeServer

```typescript
// src/main/bridge/BridgeServer.ts

import express from 'express'
import { Server } from 'http'
import * as path from 'path'
import * as fs from 'fs'
import * as os from 'os'
import { WindowWatcher } from './WindowWatcher'
import { ClipboardWatcher } from './ClipboardWatcher'

export class BridgeServer {
  private app: express.Express
  private server: Server | null = null
  private port: number | null = null
  private readonly portFilePath: string

  private windowWatcher: WindowWatcher
  private clipboardWatcher: ClipboardWatcher

  constructor() {
    this.app = express()
    this.portFilePath = path.join(os.homedir(), '.c-claw', 'bridge.port')
    this.windowWatcher = new WindowWatcher()
    this.clipboardWatcher = new ClipboardWatcher()

    this.setupMiddleware()
    this.setupRoutes()
  }

  private setupMiddleware(): void {
    this.app.use(express.json())
  }

  private setupRoutes(): void {
    // Window Watcher
    this.app.get('/bridge/window/active', async (_req, res) => {
      try {
        const info = await this.windowWatcher.getActiveWindow()
        res.json(info)
      } catch (err) {
        res.status(500).json({ error: 'Failed to get active window' })
      }
    })

    // Clipboard — GET
    this.app.get('/bridge/clipboard', (_req, res) => {
      const content = this.clipboardWatcher.readClipboard()
      res.json({ content, timestamp: Date.now() })
    })

    // Clipboard — POST (write)
    this.app.post('/bridge/clipboard', (req, res) => {
      const { content } = req.body
      if (typeof content !== 'string') {
        res.status(400).json({ error: 'content must be a string' })
        return
      }
      this.clipboardWatcher.writeClipboard(content)
      res.json({ success: true })
    })

    // Clipboard — SSE events
    this.app.get('/bridge/clipboard/events', (req, res) => {
      res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive'
      })

      const listener = (content: string) => {
        res.write(`event: clipboard-change\ndata: ${JSON.stringify({ content, timestamp: Date.now() })}\n\n`)
      }

      this.clipboardWatcher.on('change', listener)

      req.on('close', () => {
        this.clipboardWatcher.off('change', listener)
      })
    })
  }

  async start(): Promise<number> {
    return new Promise((resolve, reject) => {
      // 随机端口
      this.server = this.app.listen(0, '127.0.0.1', () => {
        const addr = this.server!.address()
        if (typeof addr === 'object' && addr) {
          this.port = addr.port
          this.writePortFile()
          this.clipboardWatcher.start()  // 开始轮询剪贴板
          console.log(`[bridge] System Bridge started on port ${this.port}`)
          resolve(this.port)
        } else {
          reject(new Error('Failed to get server address'))
        }
      })

      this.server.on('error', reject)
    })
  }

  private writePortFile(): void {
    const dir = path.dirname(this.portFilePath)
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true })
    }
    fs.writeFileSync(this.portFilePath, String(this.port))
    console.log(`[bridge] Port written to ${this.portFilePath}`)
  }

  getPort(): number | null {
    return this.port
  }

  async stop(): Promise<void> {
    this.clipboardWatcher.stop()
    if (this.server) {
      return new Promise(resolve => {
        this.server!.close(() => {
          console.log('[bridge] System Bridge stopped')
          // 清理端口文件
          try { fs.unlinkSync(this.portFilePath) } catch {}
          resolve()
        })
      })
    }
  }
}
```

**关键设计决策：**

1. **端口分配**：使用 `app.listen(0, '127.0.0.1')` 让 OS 分配随机可用端口，避免端口冲突。
2. **监听地址**：强制绑定 `127.0.0.1`，不暴露到局域网。
3. **端口文件**：与 Java 后端的 `~/.c-claw/port` 并列使用 `~/.c-claw/bridge.port`。
4. **SSE 连接管理**：每个 SSE 客户端注册 change 事件监听器，客户端断开时自动移除。
5. **生命周期**：BridgeServer 先于 BrowserWindow 启动，在 `before-quit` 时关闭。

### 5.2 WindowWatcher

```typescript
// src/main/bridge/WindowWatcher.ts

import activeWin from 'active-win'

export interface ActiveWindowInfo {
  title: string
  processName: string     // 进程名（如 chrome.exe）
  className?: string      // 窗口类名（Windows）
  pid?: number            // 进程ID
}

export class WindowWatcher {
  async getActiveWindow(): Promise<ActiveWindowInfo> {
    const result = await activeWin()

    if (!result) {
      return { title: '', processName: '' }
    }

    return {
      title: result.title,
      processName: result.owner.name,
      ...(result.owner.processId ? { pid: result.owner.processId } : {}),
    }
  }
}
```

**active-win 返回值结构（TypeScript）：**

```typescript
interface Result {
  title: string
  id: number
  bounds: { x: number; y: number; width: number; height: number }
  owner: {
    name: string           // 进程名（如 "chrome.exe"）
    processId: number
    path: string           // 可执行文件路径
  }
  memoryUsage: number
}
```

**兼容性：** active-win 支持 Windows（user32.dll）、macOS（CGWindow）、Linux（X11），与 Electron 33 兼容。

### 5.3 ClipboardWatcher

```typescript
// src/main/bridge/ClipboardWatcher.ts

import { clipboard, EventEmitter } from 'electron'

export class ClipboardWatcher extends EventEmitter {
  private pollInterval: NodeJS.Timeout | null = null
  private lastContent: string = ''
  private readonly POLL_MS = 500

  start(): void {
    // 初始化当前剪贴板内容
    this.lastContent = clipboard.readText()

    this.pollInterval = setInterval(() => {
      try {
        const current = clipboard.readText()
        if (current !== this.lastContent) {
          this.lastContent = current
          this.emit('change', current)
        }
      } catch {
        // 剪贴板读取可能失败（如内容非文本），忽略
      }
    }, this.POLL_MS)
  }

  stop(): void {
    if (this.pollInterval) {
      clearInterval(this.pollInterval)
      this.pollInterval = null
    }
  }

  readClipboard(): string {
    try {
      this.lastContent = clipboard.readText()
      return this.lastContent
    } catch {
      return ''
    }
  }

  writeClipboard(content: string): void {
    clipboard.writeText(content)
    this.lastContent = content
  }
}
```

**设计说明：**

- **轮询间隔 500ms**：Electron 剪贴板 API 没有原生变更事件，轮询是必要的。500ms 在响应性和 CPU 开销间取得平衡。
- **EventEmitter 继承**：ClipboardWatcher 继承 Node EventEmitter，统一事件模型，便于 BridgeServer 的 SSE 端点订阅。
- **初始化捕获**：`start()` 时立即读取当前剪贴板内容作为 baseline。
- **写入保护**：`writeClipboard` 同时更新 `lastContent`，避免自己写入触发 change 事件。
- **异常处理**：`readClipboard` 在非文本内容时可能失败（如图片），静默忽略。

### 5.4 index.ts (bridge re-export)

```typescript
// src/main/bridge/index.ts

export { BridgeServer } from './BridgeServer'
export { WindowWatcher } from './WindowWatcher'
export { ClipboardWatcher } from './ClipboardWatcher'
```

---

## 六、主进程集成修改

### 6.1 src/main/index.ts 变更

```typescript
import { app, BrowserWindow, ipcMain } from 'electron'
import { join } from 'path'
import { createTray } from './tray'
import { JavaProcess } from './java-launcher'
import { BridgeServer } from './bridge'

let mainWindow: BrowserWindow | null = null
let javaProcess: JavaProcess | null = null
let bridgeServer: BridgeServer | null = null

// ... createWindow() 不变 ...

app.whenReady().then(async () => {
  ipcMain.handle('get-backend-port', () => javaProcess?.getPort() ?? null)
  ipcMain.handle('get-bridge-port', () => bridgeServer?.getPort() ?? null)

  // 启动 Java 后端
  javaProcess = new JavaProcess()
  try {
    await javaProcess.start()
  } catch (err) {
    console.error('[c-claw] Failed to start Java backend:', err)
  }

  // 启动 System Bridge
  bridgeServer = new BridgeServer()
  try {
    await bridgeServer.start()
  } catch (err) {
    console.error('[c-claw] Failed to start System Bridge:', err)
  }

  createWindow()
  createTray(mainWindow!)

  // ... activate / window-all-closed 不变 ...
})

app.on('before-quit', () => {
  if (bridgeServer) {
    bridgeServer.stop()
  }
  if (javaProcess) {
    javaProcess.stop()
  }
})
```

### 6.2 src/preload/index.ts 变更

```typescript
import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('electronAPI', {
  getBackendPort: () => ipcRenderer.invoke('get-backend-port'),
  getBridgePort: () => ipcRenderer.invoke('get-bridge-port'),
  platform: process.platform
})
```

### 6.3 src/renderer/env.d.ts 变更

```typescript
/// <reference types="vite/client" />

declare global {
    interface Window {
        electronAPI?: {
            getBackendPort: () => Promise<number>
            getBridgePort: () => Promise<number>
            platform: string
        }
    }
}

export {}
```

---

## 七、依赖变更

### 7.1 package.json

```json
{
  "dependencies": {
    "active-win": "^9.0.1",
    "dompurify": "^3.2.6",
    "express": "^4.21.0",
    "marked": "^15.0.12",
    "pinia": "^2.3.1",
    "vue": "^3.5.35"
  },
  "devDependencies": {
    "@types/express": "^5.0.0",
    "@vitejs/plugin-vue": "^5.0.0",
    "electron": "^33.0.0",
    "electron-builder": "^25.0.0",
    "electron-vite": "^2.0.0"
  }
}
```

**安装命令：**

```bash
cd electron-app
npm install active-win express
npm install -D @types/express
```

### 7.2 electron-builder 注意事项

`active-win` 是原生 Node addon（使用 N-API），electron-builder 打包时需要确保原生模块针对 Electron 的 Node 版本重新编译。需要在 `electron-builder.yml` 中添加：

```yaml
npmRebuild: true
```

如果 `active-win` 使用了预编译二进制，可能还需要在 `electron-builder.yml` 中排除再重新安装：

```yaml
nodeGypRebuild: true
```

或者在构建前执行 `npx electron-rebuild`。

---

## 八、路由表

| Method | Path | 功能 | 请求体 | 响应体 |
|--------|------|------|--------|--------|
| GET | `/bridge/window/active` | 获取当前活动窗口信息 | - | `{ title, processName, pid? }` |
| GET | `/bridge/clipboard` | 读取当前剪贴板文本 | - | `{ content, timestamp }` |
| POST | `/bridge/clipboard` | 写入文本到剪贴板 | `{ content }` | `{ success: true }` |
| GET | `/bridge/clipboard/events` | 剪贴板变更 SSE 流 | - | `event: clipboard-change\ndata: { content, timestamp }` |

---

## 九、数据流

### 9.1 Java 后端发现 Bridge 端口

```
BridgeServer.start()
  → Express 监听 127.0.0.1:0（OS分配端口）
  → 写入 ~/.c-claw/bridge.port
  → Java 后端启动后读取 bridge.port
  → 后续所有 /bridge/* 请求发往该端口
```

### 9.2 活动窗口查询

```
Java Backend ──GET /bridge/window/active──▶ BridgeServer
                                               └──▶ WindowWatcher.getActiveWindow()
                                                       └──▶ activeWin() (调用原生API)
                                                       ◀── { title, owner.name, ... }
                                               ◀── JSON Response
Java Backend ◀────────────────────────────────
```

### 9.3 剪贴板读取/写入

```
Java Backend ──GET /bridge/clipboard──▶ BridgeServer
                                          └──▶ ClipboardWatcher.readClipboard()
                                                 └──▶ clipboard.readText()
                                          ◀── { content, timestamp }
Java Backend ◀───────────────────────────

Java Backend ──POST /bridge/clipboard {content}──▶ BridgeServer
                                                     └──▶ ClipboardWatcher.writeClipboard(content)
                                                            └──▶ clipboard.writeText(content)
                                                     ◀── { success: true }
Java Backend ◀──────────────────────────────────────
```

### 9.4 剪贴板 SSE 推送

```
Java Backend ──GET /bridge/clipboard/events──▶ BridgeServer (SSE 连接建立)
                                                  └──▶ ClipboardWatcher.on('change', listener)
                  
[500ms 后剪贴板变化]
ClipboardWatcher.poll() → 检测到变化 → emit('change', newContent)
  → SSE listener → res.write("event: clipboard-change\ndata: {...}\n\n")

Java Backend ◀── SSE stream ────────────────
```

---

## 十、启动顺序与生命周期

```
app.whenReady()
  │
  ├── 1. JavaProcess.start()
  │       └── 轮询等待 ~/.c-claw/port → 健康检查
  │
  ├── 2. BridgeServer.start()
  │       └── Express.listen(0, '127.0.0.1') → 写入 ~/.c-claw/bridge.port
  │       └── ClipboardWatcher.start() 开始轮询
  │
  ├── 3. createWindow() → BrowserWindow 显示
  │
  └── 4. createTray() → 系统托盘

app.on('before-quit')
  ├── BridgeServer.stop()
  │     └── ClipboardWatcher.stop()
  │     └── Server.close() → 删除 bridge.port
  └── JavaProcess.stop()
        └── 发送 SIGTERM → 10s 超时强制 SIGKILL
```

---

## 十一、security 考量

| 措施 | 说明 |
|------|------|
| 绑定 127.0.0.1 | HTTP 服务仅监听回环地址，不暴露到网络 |
| 禁用 CORS | 不需要，同机 localhost 通信无需跨域 |
| 端口随机分配 | `listen(0)` 避免固定端口冲突和扫描 |
| Express body 限制 | `express.json()` 默认 100kb 限制足够 |
| 无认证 | 仅本地进程间通信，无需额外认证层 |
| 剪贴板写入 | POST `/bridge/clipboard` 直接写入，信任本地调用方（Java 后端） |

---

## 十二、错误处理策略

| 场景 | 处理方式 |
|------|----------|
| active-win 调用失败 | 返回 `{ title: '', processName: '' }`，不抛异常 |
| 剪贴板读取失败（非文本） | 返回空字符串 `''` |
| Bridge 端口已被占用 | `listen(0)` 使用随机端口，冲突概率极低 |
| Bridge Server 启动失败 | 记录错误日志，不影响窗口显示和 Java 后端 |
| SSE 客户端断开 | 自动移除事件监听器，无资源泄漏 |
| 端口文件写入失败 | 记录错误，但不阻止服务启动 |

---

## 十三、测试要点

| 测试项 | 验证方式 |
|--------|----------|
| Bridge Server 启动 | `curl http://127.0.0.1:{port}/bridge/window/active` 返回 JSON |
| 活动窗口检测 | 切换到不同窗口后调用接口，验证 title/processName 正确 |
| 剪贴板读取 | 复制文本后 GET `/bridge/clipboard`，验证 content 正确 |
| 剪贴板写入 | POST 写入文本后，手动 Ctrl+V 验证可粘贴 |
| SSE 推送 | 建立 SSE 连接后复制新文本，验证实时收到事件 |
| 端口文件 | 检查 `~/.c-claw/bridge.port` 存在且内容为有效端口号 |
| 优雅关闭 | 退出应用后检查 bridge.port 已删除，进程无残留 |
| 启动失败不阻塞 | 若 active-win 不可用，应用仍可正常启动和聊天 |

---

## 十四、潜在风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| active-win 原生模块与 Electron 33 ABI 不兼容 | WindowWatcher 功能不可用 | 启动时 try-catch，失败不影响其他功能；可降级为 PowerShell 方案 |
| 剪贴板高频轮询 CPU 开销 | 轻微性能影响 | 500ms 间隔已很低，实测 CPU < 0.1% |
| SSE 连接未及时清理 | 内存泄漏 | req.on('close') 确保监听器移除 |
| electron-builder 打包 native addon | 打包后运行崩溃 | 使用 electron-rebuild 或配置 npmRebuild |

---

## 十五、后续 Phase 扩展预留

Phase 2-3 将新增的 System Bridge 控制器：

| Controller | 功能 | 预留路由前缀 |
|------------|------|-------------|
| ProcessController | 进程列表、进程管理 | `/bridge/process/*` |
| FileSystemController | 文件读取、目录浏览 | `/bridge/fs/*` |
| InputController | 键盘/鼠标模拟 | `/bridge/input/*` |

当前 BridgeServer 的 `setupRoutes()` 方法设计为易于扩展：新增控制器只需在构造函数中创建实例，在 `setupRoutes()` 中注册路由即可。

---

## 十六、文件变更总览

| 操作 | 文件 | 行数估算 |
|------|------|----------|
| 新增 | `src/main/bridge/index.ts` | ~5 |
| 新增 | `src/main/bridge/BridgeServer.ts` | ~100 |
| 新增 | `src/main/bridge/WindowWatcher.ts` | ~35 |
| 新增 | `src/main/bridge/ClipboardWatcher.ts` | ~55 |
| 修改 | `src/main/index.ts` | +15 |
| 修改 | `src/preload/index.ts` | +1 |
| 修改 | `src/renderer/env.d.ts` | +1 |
| 修改 | `package.json` | +2 dependencies |
| **合计** | **8 个文件** | **~200 行新增代码** |