# Competitive Analysis: OpenClaw vs Hermes

Date: 2026-05-28

---

## 1. OpenClaw

### One-Line Summary

A self-hosted, personal AI assistant running as a local-first gateway with 25+ messaging channels, 5,400+ community skills, and companion apps for desktop and mobile.

### Core Details

| Attribute | Detail |
|-----------|--------|
| Repository | `github.com/openclaw/openclaw` |
| Stars | ~375,000 |
| License | MIT |
| Language | TypeScript (monorepo with pnpm workspaces) |
| Runtime | Node.js 24 (recommended), Node 22.19+ minimum |
| Setup time | Under 30 minutes |

### Key Features

- **Gateway Architecture**: A central daemon (launchd/systemd) that manages sessions, channels, tools, and events. All components connect over WebSocket.
- **25+ Messaging Channels**: WhatsApp, Telegram, Slack, Discord, Google Chat, Signal, iMessage, IRC, Microsoft Teams, Matrix, Feishu, LINE, Mattermost, Nextcloud Talk, Nostr, Synology Chat, Tlon, Twitch, Zalo, Zalo Personal, WeChat, QQ, WebChat.
- **Voice & Speech**: Wake word detection (macOS/iOS), continuous voice interaction (Android), ElevenLabs integration with system TTS fallback.
- **Live Canvas**: Agent-driven visual workspace (A2UI) on macOS -- renders and controls a visual interface.
- **Companion Apps**: macOS menu bar app, iOS node, Android node -- all optional, pairing over the Gateway WebSocket.
- **48 Native Tools**: Browser automation, canvas control, node management, cron jobs, session tools (list, history, send, spawn), Discord/Slack actions.
- **Model Providers**: OpenAI, Anthropic, Google, Azure OpenAI, Ollama, and OpenAI-compatible endpoints. Supports auth profile rotation and failover.
- **Operator Chat Commands**: `/status`, `/new`, `/reset`, `/compact`, `/think`, `/verbose`, `/trace`, `/usage`, `/restart`, `/activation`.

### Tech Stack / Architecture

- **Monorepo** with `apps/`, `src/`, `packages/`, `extensions/`, `ui/`, `skills/`, `config/`, `deploy/`, `docs/`
- **Build system**: TypeScript via `tsx` for dev, `tsdown` for production builds
- **Testing**: Vitest
- **Linting**: oxlint, oxfmt
- **Config**: JSON at `~/.openclaw/openclaw.json`
- **Deployment**: Docker, Fly.io, Render, Nix
- **Security**: Semgrep scanning, GitHub Actions CI

### How Skills/Plugins Work

**Skills** are markdown-based capability extensions stored in `~/.openclaw/workspace/skills/<skill>/SKILL.md`. The system uses layered prompt injection:

- **Bundled skills**: Ship with the product
- **Managed skills**: Pulled from ClawHub registry (`clawhub.ai`) via `clawhub install <slug>`
- **Workspace skills**: User-created, local to the workspace

A community-curated list of **5,400+ skills** exists at `github.com/VoltAgent/awesome-openclaw-skills`, sourced from over 13,729 registry entries (spam/duplicates/low-quality/malicious filtered out). Skills span 30+ categories with the largest being Coding Agents (1,184), Web Development (919), and DevOps (393).

**Extensions** live in the `extensions/` directory. Channel plugins (WhatsApp, Telegram, Discord, etc.) are implemented as extension modules. A Plugin SDK provides type definitions for third-party development.

### How Memory Works

Memory is session-based and workspace-persistent:

- **Sessions**: Each conversation lives in an isolated session. `sessions_history` retrieves past interactions; `sessions_send` enables cross-session messaging.
- **Workspace persistence**: `~/.openclaw/workspace/` stores `AGENTS.md` (instructions), `SOUL.md` (identity), and `TOOLS.md` -- injected into every session's context.
- **Compact**: The `/compact` chat command manages context window by summarizing conversation history.
- **Allowlist store**: DM pairing creates a persistent local allowlist for approved senders.

Notable: OpenClaw itself does not include a built-in vector database or knowledge graph. The ecosystem fills this gap:

- **GBrain** (19.5k stars): A knowledge graph brain layer with PGLite/Postgres+pgvector backend, hybrid search (vector+keyword+RRF+reranker), auto-linking entity graph, cron-driven enrichment, and OAuth-scoped multi-user access.
- **memU** (13.7k stars): A three-layer hierarchical memory framework for 24/7 proactive agents with RAG-based and LLM-based retrieval modes. Runs a sidecar "MemU Bot" alongside the main agent.

### Desktop Client Approach

- **macOS**: Native menu bar app with Voice Wake, push-to-talk overlay, WebChat, debug tools, remote gateway control over SSH.
- **iOS node**: Voice trigger forwarding, Canvas surface, Gateway WebSocket pairing.
- **Android node**: Connect/Chat/Voice tabs, Canvas, Camera, Screen capture, device command families.
- **Third-party**: ClawWork (desktop multi-session productivity client), ClawPier (Tauri v2 sandboxed Docker agent manager supporting both OpenClaw and Hermes), Lumi (Rust+GPUI unified desktop UI for multiple agents).

All companion apps are optional -- the core gateway runs headless and can be used purely via chat channels.

### Notable Design Decisions

- **Local-first, always-on**: The gateway runs as a daemon on your own hardware. No cloud dependency required (though cloud deployment is supported).
- **Security by default**: Tools run on-host for the `main` session only. Non-main sessions can be sandboxed via Docker, SSH, or OpenShell. DM pairing requires explicit approval. URL safety filtering blocks loopback/private/multicast addresses.
- **Single-user focus**: Designed for personal use, not team collaboration (though GBrain adds multi-user brain capabilities).
- **Strong Chinese ecosystem**: Multiple Chinese localization repos, Feishu/QQ/OneBot integrations, and ~4.3k-star use-case collections. OpenClaw.NET explicitly supports Chinese community patterns.
- **Vibe-coding friendly**: Explicitly welcomes AI-generated PRs.

### Ecosystem (Notable Related Projects)

| Project | Stars | Purpose |
|---------|-------|---------|
| GBrain | 19.5k | Knowledge graph brain with hybrid search, cron enrichment |
| memU | 13.7k | 24/7 proactive agent memory framework |
| awesome-openclaw-skills | 49.4k | 5,400+ curated skills catalog |
| awesome-openclaw-agents | 3.5k | 205 SOUL.md agent templates |
| goclaw | 586 | Go-language reimplementation |
| OpenClaw.NET | 350 | .NET NativeAOT runtime with governance features |
| ClawPier | 73 | Tauri v2 sandboxed agent desktop manager |
| ClawWork | 511 | Desktop multi-session productivity client |
| openclaw-nerve | 822 | Real-time web cockpit with voice, kanban board |

---

## 2. Hermes (Nous Research)

### One-Line Summary

A self-improving AI agent by Nous Research with a built-in learning loop that autonomously creates, refines, and shares skills, backed by dialectic user modeling across 6 terminal backends and multiple messaging platforms.

### Core Details

| Attribute | Detail |
|-----------|--------|
| Repository | `github.com/NousResearch/hermes-agent` |
| License | MIT |
| Language | Python (89%), TypeScript (8.2%) |
| Package Manager | uv (Python 3.11+) |
| Modules | `agent/`, `gateway/`, `skills/`, `tools/`, `providers/`, `plugins/`, `cron/`, `web/`, `hermes_cli/` |

### Key Features

- **Built-in Learning Loop**: After complex tasks, the agent autonomously creates skills. Skills self-improve during subsequent use. This is the headline differentiator.
- **Skill System (Procedural Memory)**: Compatible with `agentskills.io` open standard. Skills browsable via `/skills` and invoked via `/<skill-name>`. Stored at `~/.hermes/skills/`. Community sharing via Skills Hub at agentskills.io.
- **Memory Layers**: (1) Persistent agent-curated records with periodic nudges, (2) FTS5 full-text session search with LLM summarization for cross-session recall, (3) Honcho dialectic user modeling (from Plastic Labs) for evolving user profiles, (4) Context files like MEMORY.md and USER.md. Can import from OpenClaw during migration.
- **40+ Tools**: Organized through a toolset system. Configurable via `hermes tools`. Subagent spawning for parallel workstreams. Python scripts can call tools through RPC, collapsing multi-step pipelines into zero-context-cost turns.
- **6 Terminal Backends**: Local, Docker, SSH, Singularity, Modal, and Daytona. Modal and Daytona offer serverless persistence with hibernation when idle.
- **Multi-Platform Messaging**: Telegram, Discord, Slack, WhatsApp, Signal, and CLI through a single gateway process. Voice memo transcription. Cross-platform conversation continuity.
- **TUI**: Full terminal interface with multiline editing, slash-command autocomplete, conversation history, interrupt-and-redirect, streaming tool output.
- **Built-in Cron Scheduler**: Natural language automation for daily reports, backups, audits. Deliverable to any platform.
- **Model-Agnostic**: Nous Portal, OpenRouter (200+ models), NovitaAI, NVIDIA NIM, Hugging Face, OpenAI, custom endpoints. Switch via `hermes model` with no code changes.
- **MCP Protocol Support**: Connects to any MCP server. Has dedicated `mcp_serve.py`, `acp_adapter/`, `acp_registry/` directories.
- **Research Features**: Batch trajectory generation and trajectory compression for training tool-calling models.
- **Nix Flakes**: Alternative environment management for reproducible setups.

### Tech Stack / Architecture

- **Python 3.11+** with `uv` package manager (pyproject.toml)
- **Node.js** as a dependency
- **ripgrep** and **ffmpeg** as system dependencies
- **Docker** for containerized deployment
- **Nix flakes** for alternative environment
- Dependency groups: `.[all]`, `.[termux]`, `.[dev]`
- Key modules: `agent/`, `gateway/`, `skills/`, `tools/`, `providers/`, `plugins/`, `cron/`, `web/`, `hermes_cli/`

### How Skills/Plugins Work

Skills are a **procedural memory system** and Hermes's central differentiator:

1. **Autonomous creation**: After completing a complex task, the agent writes a skill capturing the procedure.
2. **Self-improvement**: Skills are refined each time they are used, becoming more robust.
3. **Open standard**: Compatible with `agentskills.io`, allowing cross-agent skill portability.
4. **Discovery**: `/skills` browses installed skills; `/<skill-name>` invokes directly.
5. **Community sharing**: Skills Hub at agentskills.io for publishing and discovery.
6. **Storage**: `~/.hermes/skills/` with both core `skills/` and `optional-skills/` directories.
7. **Plugin architecture**: Open plugin system via `plugins/` and `optional-mcps/` directories.

### How Memory Works

Hermes has the more sophisticated memory architecture of the two:

| Layer | Mechanism | Purpose |
|-------|-----------|---------|
| Persistent memory | Agent-curated records + periodic nudges | Remembers important facts, preferences |
| Session search | FTS5 full-text + LLM summarization | Cross-session recall and context |
| User modeling | Honcho (Plastic Labs) dialectic framework | Evolving user profile, preferences, patterns |
| Context files | MEMORY.md + USER.md + AGENTS.md | Inject persistent context into every conversation |

Migration path: Can import MEMORY.md and USER.md from OpenClaw, indicating some interoperability awareness between the two projects.

### Desktop Client Approach

**No first-party desktop client**. The core Hermes Agent is CLI/TUI-first. Third-party desktop clients have emerged:

- **Portable Hermes Agent** (143 stars): Windows desktop with 100 tools, Tkinter GUI, LM Studio local models, TTS, Music generation, ComfyUI integration, workflow engine, Tool Maker for dynamic tool creation. No install, no Docker, no admin rights required.
- **Hermes Agent Desktop** (34 stars): macOS native (pywebview) with 20 specialized AI agents (PM, engineers, QA, etc.), Visual Skill Store with 50+ skills from CocoLoop marketplace, Apple-inspired design, streaming SSE responses.
- **ClawPier** (73 stars): Tauri v2 sandboxed Docker manager supporting both Hermes and OpenClaw runtimes with unified management UI.
- **Lumi** (11 stars): Rust+GPUI unified desktop UI for Hermes, Claude Code, Gemini CLI, Crush, Codex, and Cursor.

### Notable Design Decisions

- **Learning loop as core primitive**: Unlike OpenClaw where skills are manually installed or configured, Hermes agents *create* skills autonomously. This is the most significant architectural difference.
- **Honcho user modeling**: The only agent in this comparison using a formal dialectic user modeling framework, giving it deeper personalized memory capabilities.
- **Research origins**: Features like trajectory generation and compression show its heritage as a research-first project from Nous Research.
- **Tool Gateway via Nous Portal**: Aggregates web search (Firecrawl), image generation (FAL), TTS (OpenAI), and cloud browser (Browser Use) under a single subscription -- a streamlined developer experience.
- **Serverless hibernation**: Modal and Daytona backends hibernate when idle, reducing cost for infrequently used agents.
- **RPC-based tool calls from Python scripts**: A unique optimization collapsing multi-step pipelines into zero-context-cost turns.
- **Migration-aware**: Explicitly supports importing OpenClaw memory files, showing awareness of the competitive landscape.

---

## 3. Side-by-Side Comparison

| Dimension | OpenClaw | Hermes |
|-----------|----------|--------|
| **Primary language** | TypeScript | Python |
| **Stars** | ~375,000 | Not disclosed (lower profile) |
| **Core philosophy** | Self-hosted personal assistant gateway | Self-improving agent with learning loop |
| **Setup complexity** | ~30 min, npm global install or Docker | uv-based install, requires system deps (ripgrep, ffmpeg, Node.js) |
| **Messaging channels** | 25+ | 6 (Telegram, Discord, Slack, WhatsApp, Signal, CLI) |
| **Skill system** | Manual install via ClawHub, markdown-based, static | Autonomous creation from experience, self-improving, procedural memory |
| **Skill count** | 5,400+ community skills | Community Skills Hub (agentskills.io), fewer curated skills |
| **Memory** | Session-based + workspace injection | Multi-layer: FTS5 search + LLM summarization + Honcho user modeling + context files |
| **Desktop client** | First-party macOS/iOS/Android | No first-party; third-party via Portable Hermes Agent, Hermes Agent Desktop, ClawPier |
| **Sandbox backends** | Docker, SSH, OpenShell | Docker, SSH, Singularity, Modal, Daytona (6 total, including serverless) |
| **MCP support** | Via community extensions | First-party with mcp_serve.py, acp_adapter, acp_registry |
| **Model providers** | OpenAI, Anthropic, Google, Azure, Ollama, compatible endpoints | Nous Portal, OpenRouter (200+), NovitaAI, NVIDIA NIM, Hugging Face, OpenAI, custom |
| **Standout capability** | Largest ecosystem, most channels, companion apps | Learning loop, skill auto-creation, Honcho user modeling |
| **Cron/automation** | Cron jobs tool | Built-in cron scheduler with natural language, any platform delivery |
| **Voice** | Wake word, Talk Mode, ElevenLabs | Voice memo transcription |
| **License** | MIT | MIT |

---

## 4. Key Takeaways

1. **OpenClaw dominates in breadth** -- more channels, more community skills, more companion apps, more ecosystem projects (GBrain, memU, goclaw, OpenClaw.NET, ClawWork, ClawPier). Its TypeScript/NPM foundation makes it accessible to web developers.

2. **Hermes leads in depth** -- the learning loop (autonomous skill creation, self-improvement) and Honcho-based user modeling are unique capabilities neither OpenClaw nor most other agents provide. Its Python foundation appeals to ML/AI practitioners.

3. **Memory is Hermes's advantage**: FTS5 + LLM summarization + dialectic user modeling vs OpenClaw's simpler session/workspace model (though GBrain and memU fill this gap in OpenClaw's ecosystem).

4. **Skills philosophy diverges**: OpenClaw treats skills as static, community-curated markdown files you install. Hermes treats them as living procedural memory that agents write and refine.

5. **Desktop is tertiary for both**: Neither project is fundamentally a desktop app. OpenClaw has first-party companion apps but is gateway-first. Hermes is CLI-first. Third-party desktop wrappers exist for both.

6. **Both are MIT-licensed and self-hosted**, giving users full data ownership.

7. **The two projects are interoperability-aware**: ClawPier manages both runtimes, GBrain explicitly mentions both in its README, and Hermes can import OpenClaw memory files.

8. **Neither has a strong Windows-first desktop story**: OpenClaw's first-party apps target macOS/iOS/Android. Hermes has no first-party desktop client. Portable Hermes Agent is the closest Windows-native option (Tkinter-based).

Sources:
- [OpenClaw GitHub](https://github.com/openclaw/openclaw)
- [Hermes Agent GitHub](https://github.com/NousResearch/hermes-agent)
- [GBrain GitHub](https://github.com/garrytan/gbrain)
- [Awesome OpenClaw Skills](https://github.com/VoltAgent/awesome-openclaw-skills)
- [Awesome OpenClaw Agents](https://github.com/mergisi/awesome-openclaw-agents)
- [goclaw GitHub](https://github.com/smallnest/goclaw)
- [OpenClaw.NET GitHub](https://github.com/clawdotnet/openclaw.net)
- [ClawPier GitHub](https://github.com/SebastianElvis/clawpier)
- [memU GitHub](https://github.com/NevaMind-AI/memU)
- [Portable Hermes Agent GitHub](https://github.com/aivrar/portable-hermes-agent)
- [Hermes Agent Desktop GitHub](https://github.com/Felix-Forever/hermes-agent-desktop)
- [Lumi GitHub](https://github.com/CES-Ltd/Lumi)