# C-Claw 设计方案

**日期**：2026-05-28  
**状态**：已确认  
**对标项目**：OpenClaw、Hermes

---

## 一、产品定位

C-Claw 是一个**桌面优先的通用 AI 助手**，面向技术用户（数据分析师、产品经理、运维人员等），提供自然语言驱动的桌面操控、浏览器自动化和信息处理能力。

**差异化核心**：深度桌面/OS 原生集成 + 静态定义式 Skill 生态。竞品 OpenClaw 偏向消息通道 bot，Hermes 是 CLI/TUI 优先——两者在桌面客户端体验上都存在明显空白。

**用户人群**：全覆盖，MVP 阶段优先面向技术用户的桌面助手场景。

---

## 二、系统总体架构

```
┌──────────────────────────────────────────────┐
│              Electron 桌面客户端               │
│  ┌─────────────┐ ┌──────────┐ ┌───────────┐ │
│  │  Chat UI    │ │ Skill    │ │  System   │ │
│  │  (Vue 3)    │ │ Manager  │ │  Bridge   │ │
│  │             │ │ (技能管理)│ │ (6个控制器)│ │
│  └──────┬──────┘ └────┬─────┘ └─────┬─────┘ │
│         │              │              │       │
│         └──────────────┼──────────────┘       │
│                        │ HTTP REST + SSE       │
└────────────────────────┼──────────────────────┘
                         │
┌────────────────────────┼──────────────────────┐
│           Java 后端 (本地常驻进程)              │
│  ┌──────────┐ ┌────────┐ ┌────────┐          │
│  │  Agent   │ │ Memory │ │ Skill  │          │
│  │  Engine  │ │ Store  │ │ Engine │          │
│  │(Claude SDK)│(SQLite)│ │(解释器) │          │
│  └──────────┘ └────────┘ └────────┘          │
└──────────────────────────────────────────────┘
```

**核心思路**：Electron 负责 UI 和系统级能力（快捷键、托盘、通知、截图），Java 后端负责 AI 逻辑（agent loop、memory、skill 解释执行）。两者通过 HTTP + SSE 解耦，未来如果需要远程部署，只需把 Java 后端搬到服务器，Electron 改连远程地址即可。Java 后端仅监听 127.0.0.1，不对外暴露。

---

## 三、Agent Engine 核心流程

```
用户输入 → Session Manager → Agent Loop ──────┐
  ↑                              │              │
  │                    ┌─────────┘              │
  │                    ▼                        │
  │              Prompt Builder                 │
  │              (system prompt                │
  │               + skill context              │
  │               + memory recall)             │
  │                    │                        │
  │                    ▼                        │
  │         Claude API (Anthropic SDK)          │
  │                    │                        │
  │                    ▼                        │
  │         Response Handler                    │
  │         ├─ 文本 → SSE 推客户端               │
  │         ├─ Tool Call → Tool Executor ───────┘
  │         └─ 完成 → Memory Archiver
  │                    │
  └────────────────────┘
```

### 关键设计点

| 组件 | 说明 |
|------|------|
| **Session Manager** | 每个对话窗口对应一个 session，支持多 session 并发；session 有独立的消息历史和上下文 |
| **Prompt Builder** | 组装 system prompt 时注入已安装 Skill 的能力描述 + Memory Store 检索到的相关记忆（MVP 阶段为关键词/时间匹配，后续升级向量检索） |
| **Tool Executor** | 执行 Claude 返回的 tool call，分为内置工具（文件读写、Shell 执行等）和 Skill 暴露的工具（浏览器操控、桌面操控等）；Skill 的 tool 定义来自其 YAML 声明 |
| **Memory Archiver** | 对话结束后，异步提取摘要写入 SQLite，作为后续 Memory Recall 的来源 |

---

## 四、Skill 系统

### Skill 包结构

每个 Skill 是一个目录：

```
~/.c-claw/skills/browser-control/
├── skill.yaml          # 元数据：名称、版本、描述、依赖
├── prompt.md           # 注入到 system prompt 的能力说明
├── tools.yaml          # 工具定义：名称、参数schema、执行指令
└── scripts/            # 可选的执行脚本（Python/Node）
    └── capture_screenshot.py
```

### skill.yaml 核心字段

| 字段 | 说明 |
|------|------|
| `name` / `version` / `description` | 基本信息 |
| `tools` | 暴露给 Agent 的工具列表，每个工具定义参数 schema |
| `permissions` | 需要的系统权限（如 `browser.control`、`clipboard.read`） |
| `dependencies` | 运行时依赖（如要求安装 Chrome） |

### 生命周期

```
ClawHub (技能市场) ─→ 安装 ─→ 注册 ─→ 注入 Prompt ─→ Tool Executor 调用
                        │       │        (Agent启动时)   (Agent触发时)
                        ▼       ▼
                 ~/.c-claw/   Skill Registry
                  skills/      (SQLite 表)
```

### 关键设计

- Skill 是**纯声明式**的（Markdown + YAML），不包含可执行代码（scripts 除外）
- 工具的实际执行由 Java 后端的 Skill Adapter 桥接到 Electron 的 System Bridge 完成
- 安装路径统一为 `~/.c-claw/skills/`

---

## 五、System Bridge（核心差异化）

Electron 通过 System Bridge 实现对 OS 的深度操控，共 6 个控制器：

| 控制器 | 能力 | 关键权限 |
|--------|------|----------|
| **Browser Controller** | 内嵌 Playwright，操控浏览器（导航、点击、输入、截图等） | `browser.control` |
| **Desktop Controller** | 截图、模拟点击输入、窗口管理 | `desktop.control` |
| **File System Controller** | 文件读写与搜索 | `filesystem.read` / `filesystem.write` |
| **Clipboard Watcher** | 监控剪贴板变化 + 主动写入 | `clipboard.read` / `clipboard.write` |
| **Window Watcher** | 读取当前活动窗口标题、进程名 | `window.info` |
| **Shortcut Manager** | 全局快捷键注册（默认 `Alt+Space` 唤起 claw） | — |

### 辅助组件

| 组件 | 说明 |
|------|------|
| **Tray Manager** | 系统托盘图标与菜单 |
| **Notification Manager** | 原生系统通知推送 |

所有能力通过 HTTP API 暴露给 Java 后端调用，仅监听 127.0.0.1。

---

## 六、Memory 存储设计

### 存储模型（SQLite）

| 表 | 内容 | 保留策略 |
|----|------|----------|
| `sessions` | 会话 ID、标题、创建时间、是否活跃、消息数 | 永久 |
| `messages` | 每轮对话全文（含 tool call） | 热数据保留 30 天 |
| `memories` | LLM 提取的结构化记忆（type、content、keywords、importance） | 永久 |

### 记忆类型

| 类型 | 说明 | 示例 |
|------|------|------|
| `summary` | 做了什么 | "用户要求汇总了 Q1 销售数据" |
| `preference` | 用户偏好 | "用户喜欢表格输出而非图表" |
| `fact` | 用户告知的事实 | "用户的数据库地址是 192.168.1.100" |

### 数据流

- **写入**：会话结束后 → 后台异步调 Claude（低温度）→ 提取 summary/preference/fact → 写入 `memories` 表并打上 `keywords`
- **检索**：新会话开始时 → 用用户输入关键词搜 `memories.keywords` → 按 `importance` 排序 → Top-N 注入 system prompt
- **分层策略**：热数据（近 30 天）保留全文在 `messages` 表可直接检索；冷数据（历史会话）只保留摘要，检索命中后按需回溯

### 文件层记忆

`~/.c-claw/AGENTS.md`：用户可手动编辑的全局人格指令，类似 OpenClaw 的 SOUL.md 机制。

---

## 七、端到端数据流

典型场景：用户说"帮我把桌面上这个 Excel 的数据汇总成报告"

```
用户 (Electron)                    Java 后端                     Claude API
     │                                │                              │
     │  1. POST /session/msg          │                              │
     │  "帮我汇总桌面Excel..."         │                              │
     │───────────────────────────────>│                              │
     │                                │  2. Prompt Builder:          │
     │                                │     + system prompt          │
     │                                │     + skill: desktop/browser │
     │                                │     + memory recall          │
     │                                │     + 当前消息                │
     │                                │─────────────────────────────>│
     │                                │                              │
     │                                │  3. SSE: tool_call           │
     │                                │  [window_watcher]            │
     │                                │<─────────────────────────────│
     │                                │                              │
     │  4. HTTP → System Bridge       │                              │
     │  返回活动窗口："Excel-报告.xlsx"│                              │
     │<───────────────────────────────│                              │
     │                                │                              │
     │                                │  5. SSE: tool_call           │
     │                                │  [file_read + browser_search]│
     │                                │<─────────────────────────────│
     │                                │                              │
     │  6. 执行文件读取+浏览器搜索      │                              │
     │───────────────────────────────>│                              │
     │                                │─────────────────────────────>│
     │                                │                              │
     │                                │  7. SSE: text (流式)          │
     │  8. 渲染 Markdown 报告          │  "根据数据，汇总如下..."      │
     │<───────────────────────────────│<─────────────────────────────│
     │                                │                              │
     │                                │  9. 后台：Memory Archiver     │
     │                                │     提取摘要→写入 memories    │
```

### 设计要点

- 每一步 tool call 都是 Electron 执行、Java 决策；Java 后端不直接碰系统能力
- SSE 流式推送文本和 tool_call 事件，Electron 根据事件类型渲染不同 UI（Markdown / 工具执行状态卡片）
- 用户全程只需自然说话，不需要手动切换 tool

---

## 八、安全模型

### 权限分层

| 级别 | 说明 | 示例 | 行为 |
|------|------|------|------|
| **L0** 无感 | 纯文本回复 | `chat.reply` | 自动放行 |
| **L1** 只读 | 读取系统信息 | `clipboard.read`、`window.info` | 自动放行 |
| **L2** 低风险写 | 有限写入操作 | `clipboard.write`、`file.write`(沙箱) | 自动放行 |
| **L3** 高风险需确认 | 主动操控外部系统 | `browser.control`、`shell.execute` | 弹窗确认 |
| **L4** 禁止（默认行为） | 破坏性操作 | `file.delete`(全盘) | 拒绝 |

### 关键设计

- 权限声明在 `skill.yaml` 的 `permissions` 字段，Skill 安装时注册
- L3 操作首次触发时 Electron 弹出确认框，支持 "本次允许" / "始终允许此 Skill" / "拒绝"
- 用户可在 Settings 中按 Skill 调整权限级别
- API Key 存储在 `~/.c-claw/config.yaml`，仅 Java 后端读取，Electron 不接触
- Java 后端仅监听 `127.0.0.1`

---

## 九、技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 桌面客户端 | **Electron** + **Vue 3** | 跨平台，生态成熟 |
| 后端框架 | **Spring Boot** | 稳定、生态全 |
| AI SDK | **Anthropic Java SDK** | 直接调用 Claude API |
| 通信协议 | **HTTP REST + SSE** | 简单通用，调试方便 |
| 存储 | **SQLite** (JDBC) | 轻量，零运维 |
| 浏览器自动化 | **Playwright** (Electron 内嵌) | -- |
| 打包 | **electron-builder** + **jlink** | jlink 打包精简 JRE 嵌入 Electron |

### 项目结构

```
c-claw/
├── electron-app/              # 桌面客户端
│   ├── src/
│   │   ├── main/              # Electron 主进程
│   │   │   ├── bridge/        # System Bridge (6个Controller)
│   │   │   ├── java-launcher/ # Java 进程生命周期管理
│   │   │   └── tray.ts        # 系统托盘
│   │   ├── renderer/          # Vue 3 渲染进程
│   │   │   ├── chat/          # 对话界面
│   │   │   ├── skill-mgr/     # Skill 管理面板
│   │   │   └── settings/      # 设置页
│   │   └── preload/
│   ├── electron-builder.yml   # 打包配置 (Win/Mac/Linux)
│   └── package.json
│
├── java-backend/              # Java 后端
│   ├── src/main/java/cc/claw/
│   │   ├── agent/             # Agent Engine & Loop
│   │   ├── memory/            # Memory Store + Archiver
│   │   ├── skill/             # Skill Registry + Loader
│   │   ├── api/               # HTTP REST + SSE 接口
│   │   └── config/            # 配置管理
│   ├── src/main/resources/
│   └── pom.xml
│
├── docs/
│   ├── research/              # 竞品分析等
│   │   └── competitors.md
│   └── plans/                 # 设计文档
│       └── 2026-05-28-c-claw-design.md
│
└── CLAUDE.md
```

---

## 十、MVP 路线图

### Phase 0：骨架搭建（预计 2-3 周）

| 模块 | 交付物 |
|------|--------|
| Electron 壳 | Vue 3 聊天窗口 + 系统托盘 + 基础布局 |
| Java 骨架 | Spring Boot 项目 + `/api/chat` 端点 + Anthropic SDK 对接 |
| 进程管理 | Electron 启动/停止 Java 进程，健康检查 |
| 通信 | 基础 REST `/send` → SSE 流式返回 Claude 回复 |

**目标**：Electron 里打字，能收到 Claude 流式回复。不做 tool call、不做 Memory。

### Phase 1：核心闭环（预计 3-4 周）

| 模块 | 交付物 |
|------|--------|
| Agent Loop | Tool Call 解析与执行循环 |
| System Bridge | Window Watcher + Clipboard Watcher + Shortcut Manager |
| Skill 基础 | Skill Registry + Loader + `prompt.md` 注入 |
| Memory 基础 | SQLite 消息存储 + 会话结束后摘要提取 |
| 全局快捷键 | `Alt+Space` 唤起 claw 迷你窗口 |

**目标**：用户可以说"看看当前窗口是什么"，Agent 能感知环境并回复。Skill 可以安装和注入 prompt。

### Phase 2：Skill 生态（预计 3-4 周）

| 模块 | 交付物 |
|------|--------|
| Browser Control | Playwright 内嵌，`browser.*` 系列 tool |
| Desktop Control | 截图、模拟点击输入、窗口管理 |
| File System | 读写 + 文件搜索 |
| Skill 市场 | ClawHub 基础版（安装/卸载/搜索） |
| 权限弹窗 | L3 操作确认流程 |

**目标**：对标 OpenClaw 的桌面操控能力。用户可以说"打开百度搜XX并把结果整理成表格"。

### Phase 3：记忆增强（预计 2-3 周）

| 模块 | 交付物 |
|------|--------|
| 分层存储 | 30 天热数据全文 + 历史摘要 |
| 关键词检索 | 注入相关记忆到 system prompt |
| 偏好学习 | preference 类型记忆提取与利用 |
| AGENTS.md | 支持用户自定义全局指令 |

**目标**：Agent 能跨会话记住用户偏好和事实。

### 路线图总览

```
Phase 0  →  Phase 1  →  Phase 2  →  Phase 3
"能聊天"   "能感知"    "能操控"    "有记忆"
```

---

## 十一、竞品对比

| 维度 | OpenClaw | Hermes | **C-Claw** |
|------|----------|--------|-------------|
| 技术栈 | TypeScript, Gateway 守护进程 | Python, CLI/TUI 优先 | **Java + Electron + Vue 3** |
| 目标用户 | 开发者, 多平台聊天用户 | AI 研究者, 自主 agent 探索 | **技术用户的桌面助手** |
| Skill 机制 | 静态 Markdown, ClawHub 5400+ | 自主学习的 procedural memory | **静态 YAML, ClawHub 市场** |
| Memory | 会话级文件存储 | FTS5 + LLM 总结 + Honcho 用户建模 | **SQLite 分层存储 (30天热+永久摘要)** |
| 桌面客户端 | macOS/iOS/Android 同伴应用 | 无官方桌面端 | **Electron 原生桌面 (核心差异化)** |
| 桌面操控 | 有限 | 有限 | **6 路 System Bridge 深度集成** |
| 生态渠道 | 25+ 消息平台 | 服务端部署 | **桌面优先 + 远程可选** |
| GitHub Stars | ~375k | 较小 | — |