# C-Claw 启动指南

## 环境要求

- JDK 21+
- Node.js 18+
- Windows / macOS / Linux

## 后端启动（Java Spring Boot）

```bash
cd java-backend
./gradlew bootRun
```

默认监听 `http://localhost:8080`，API 路径前缀 `/api`。

首次启动时会在 `~/.c-claw/` 下创建 SQLite 数据库 `claw.db`。

## 前端启动（Electron + Vue3）

```bash
cd electron-app
npm run dev
```

## 桥接流程

1. 前端启动后，Electron 主进程会在 `127.0.0.1` 随机端口启动 System Bridge HTTP 服务
2. 端口号写入 `~/.c-claw/bridge.port`
3. 后端读取 `bridge.port` 文件，连接桥接服务进行工具调用

## 验证

- 后端：`cd java-backend && ./gradlew test`
- 前端：`cd electron-app && npm run build`