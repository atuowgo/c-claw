# Phase 1 Agent Loop -- 实现计划

**日期**: 2026-05-29
**状态**: 待评审
**依赖**: Phase 0 已完成（基础聊天、SSE 流式回复）

---

## 一、目标

在现有 `/api/chat` SSE 接口的基础上，升级 `ClaudeService` 支持 Agent Loop（工具调用循环）。客户端接口不变 -- 循环对客户端完全透明。

**三条核心能力**：

1. Claude 可以调用 4 个内置工具获取桌面的真实信息
2. Claude 拿到工具结果后可以继续推理，直到给出最终回复
3. 整个过程流式推到客户端，用户看到文本和工具执行状态

**不在范围内**：
- Skill 系统的 tool（Phase 2）
- Memory 存储/检索（Phase 3）
- Session 管理（仅请求级）

---

## 二、架构升级全景

### 当前架构 (Phase 0)

```
ChatController → ClaudeService → Anthropic SDK (text only)
                   ↓
              SSE: text delta → 客户端
```

### 目标架构 (Phase 1)

```
ChatController → ClaudeService ──── loop ────→ Anthropic SDK (with tools)
                   ↓    ↑                        ↓
              SSE  │    │ tool_result      tool_use event
              text │    └── ToolExecutor ←── 解析 JSON input
              tool │         ├─ window_watcher   → GET  http://127.0.0.1:{bridge-port}/api/window/current
              done │         ├─ clipboard_read   → GET  http://127.0.0.1:{bridge-port}/api/clipboard
                   │         ├─ clipboard_write  → POST http://127.0.0.1:{bridge-port}/api/clipboard
                   │         └─ shortcut_register→ POST http://127.0.0.1:{bridge-port}/api/shortcut/register
                   ↓
              客户端（接收 SSE，渲染文本 + 工具状态卡片）
```

---

## 三、新建文件清单

### 3.1 `cc/claw/agent/tool/ToolDefinition.java`

```java
package cc.claw.agent.tool;

import com.anthropic.models.messages.Tool;

/**
 * 工具定义 -- 封装 Anthropic SDK 的 Tool 对象，附加元数据。
 */
public record ToolDefinition(
    String name,
    String description,
    String inputSchemaJson,  // JSON Schema 字符串，描述参数
    Tool anthropicTool       // 预构建的 Anthropic SDK Tool 对象（避免每次请求重复构建）
) {
    /**
     * 工厂方法：从 name、description、JSON Schema 构建。
     * 内部调用 Tool.builder() 构建 Anthropic tool。
     */
    public static ToolDefinition of(String name, String description, String inputSchemaJson);
}
```

### 3.2 `cc/claw/agent/tool/ToolResult.java`

```java
package cc.claw.agent.tool;

/**
 * 工具执行结果。
 */
public record ToolResult(
    String toolUseId,    // Claude 返回的 tool_use id，必须原样回传
    String toolName,     // 工具名
    String content,      // 执行结果（成功时为 JSON 字符串，失败时为错误信息）
    boolean success      // 是否执行成功
) {
    public static ToolResult success(String toolUseId, String toolName, String content);
    public static ToolResult failure(String toolUseId, String toolName, String error);
}
```

### 3.3 `cc/claw/agent/tool/ToolExecutor.java`

```java
package cc.claw.agent.tool;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 工具执行器接口。
 * Phase 1 实现：BuiltinToolExecutor。
 * Phase 2 扩展：SkillToolExecutor。
 */
public interface ToolExecutor {

    /**
     * 返回此执行器支持的所有工具定义。
     */
    List<ToolDefinition> getToolDefinitions();

    /**
     * 判断是否能执行指定工具。
     * 用于 Agent Loop 分发时快速判断。
     */
    boolean canExecute(String toolName);

    /**
     * 执行工具，返回异步结果。
     *
     * @param toolUseId Claude 返回的 tool_use id
     * @param toolName  工具名称
     * @param arguments 工具参数的 JSON 字符串（Claude 给的 input）
     * @return 工具执行结果
     */
    CompletableFuture<ToolResult> execute(String toolUseId, String toolName, String arguments);
}
```

### 3.4 `cc/claw/agent/tool/BuiltinToolExecutor.java`

```java
package cc.claw.agent.tool;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Phase 1 内置工具执行器。
 * 通过 HTTP 调用 Electron 的 System Bridge 完成实际操作。
 *
 * 4 个内置工具：
 * - window_watcher     → GET  {bridgeUrl}/api/window/current
 * - clipboard_read     → GET  {bridgeUrl}/api/clipboard
 * - clipboard_write    → POST {bridgeUrl}/api/clipboard  body: {"text": "..."}
 * - shortcut_register  → POST {bridgeUrl}/api/shortcut/register  body: {"key":"...","action":"..."}
 */
@Component
public class BuiltinToolExecutor implements ToolExecutor {

    private final RestTemplate restTemplate;
    private final String bridgeUrl;          // http://127.0.0.1:{bridgePort}
    private final List<ToolDefinition> tools;

    /**
     * 构造函数。
     * bridgePort 从 ClawConfig 注入（默认 19800，Electron 启动时写入配置）。
     *
     * 在构造函数中调用 BuiltinToolDefinitions.all() 初始化 tools 列表。
     */
    public BuiltinToolExecutor(ClawConfig config);

    @Override
    public List<ToolDefinition> getToolDefinitions() { return tools; }

    @Override
    public boolean canExecute(String toolName) {
        return tools.stream().anyMatch(t -> t.name().equals(toolName));
    }

    @Override
    public CompletableFuture<ToolResult> execute(String toolUseId, String toolName, String arguments);

    // -- private helpers --

    /** 检查 clipboard_write 文本是否超出合理长度（如 100KB），超出则拒绝。 */
    private void validateClipboardWrite(String arguments);

    /** 检查 shortcut_register 的 key 格式是否合法（由 Electron 端校验，这里做基础校验）。 */
    private void validateShortcutRegister(String arguments);
}
```

### 3.5 `cc/claw/agent/tool/BuiltinToolDefinitions.java`

```java
package cc.claw.agent.tool;

import java.util.List;

/**
 * Phase 1 四个内置工具的静态定义。
 * 所有 ToolDefinition 在此集中声明，被 BuiltinToolExecutor 消费。
 * Anthropic Tool 对象在类加载时构建（不可变，线程安全）。
 */
public final class BuiltinToolDefinitions {

    private BuiltinToolDefinitions() {}

    public static List<ToolDefinition> all();

    // -- 四个工具定义 --
    //
    // 1. window_watcher
    //    description: "获取当前活动窗口信息，包括窗口标题、进程名、窗口类名"
    //    参数: 无（空对象）
    //
    // 2. clipboard_read
    //    description: "读取系统剪贴板的当前文本内容"
    //    参数: 无（空对象）
    //
    // 3. clipboard_write
    //    description: "向系统剪贴板写入文本内容"
    //    参数: {"type":"object","properties":{"text":{"type":"string","description":"要写入的文本"}},"required":["text"]}
    //
    // 4. shortcut_register
    //    description: "注册全局快捷键（键值已在 Electron 端预设，此处仅触发注册确认）"
    //    参数: {"type":"object","properties":{"key":{"type":"string","description":"快捷键组合，如 Alt+Space"}},"required":["key"]}

    // 注意：每个 ToolDefinition 内部通过 Tool.builder() 构建 Anthropic Tool，
    // JSON Schema 使用 JsonObject 构建或直接用 Map 转 Schema
}
```

---

## 四、修改文件清单

### 4.1 `cc/claw/agent/ClaudeService.java` -- 核心改造

**改动量最大**。将当前单次流式调用升级为带 tool loop 的多次循环。

```java
@Service
public class ClaudeService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);
    private static final String SYSTEM_PROMPT = """
        You are C-Claw, a helpful desktop AI assistant.
        You help users with their tasks. Be concise and helpful.
        You run locally on the user's computer.
        You have access to tools to interact with the user's desktop,
        including reading window information, clipboard access, and shortcut management.
        Use these tools when the user's request requires desktop interaction.
        """;

    private static final int MAX_TOOL_ROUNDS = 10;  // 防止无限循环
    private static final String MODEL = "claude-sonnet-4-20250514";

    private final AnthropicClient client;
    private final AsyncTaskExecutor executor;
    private final ToolExecutor toolExecutor;        // 新增：注入 ToolExecutor

    public ClaudeService(AnthropicClient client,
                         AsyncTaskExecutor executor,
                         ToolExecutor toolExecutor) {
        this.client = client;
        this.executor = executor;
        this.toolExecutor = toolExecutor;
    }

    /**
     * 流式对话，支持 Agent Loop。
     * 
     * @param userMessage  用户消息
     * @param onDelta      文本 delta 回调
     * @param onToolCall   工具调用回调（name, toolUseId） -- 新增，用于 UI 展示
     * @param onToolResult 工具结果回调（name, success, content） -- 新增
     * @param onError      错误回调
     * @param onComplete   完成回调
     */
    public void streamMessage(String userMessage,
                              Consumer<String> onDelta,
                              Consumer<ToolCallInfo> onToolCall,      // 新增
                              Consumer<ToolResultInfo> onToolResult,  // 新增
                              Consumer<Throwable> onError,
                              Runnable onComplete) {
        executor.execute(() -> {
            try {
                runAgentLoop(userMessage, onDelta, onToolCall, onToolResult);
                onComplete.run();
            } catch (Exception e) {
                log.error("Agent loop error", e);
                onError.accept(e);
            }
        });
    }

    /**
     * Agent Loop 核心逻辑。
     * 伪代码：
     *
     * messages = [userMessage]
     * for round in 1..MAX_TOOL_ROUNDS:
     *     currentToolUses = []
     *     stream = client.messages().createStreaming(params(messages, tools))
     *     for event in stream:
     *         if content_block_start.tool_use:
     *             记录 toolUseId, toolName，通知 onToolCall
     *         if content_block_delta.text:
     *             通知 onDelta
     *         if content_block_delta.input_json:
     *             累积到 currentToolUses[对应的 toolUseId].inputJsonBuilder
     *         if content_block_stop:
     *             标记该 block 完成
     *     
     *     if currentToolUses 为空:
     *         break  // 纯文本回复，没有工具调用，结束
     *     
     *     // 执行所有工具调用
     *     assistantContent = [tool_use blocks from current response]
     *     messages.append(Message.assistant(assistantContent))
     *     
     *     for toolUse in currentToolUses:
     *         result = toolExecutor.execute(toolUse.id, toolUse.name, toolUse.input)
     *         通知 onToolResult
     *         messages.append(Message.user(tool_result(result)))
     *
     * // 超过最大轮次仍未结束 → 返回兜底文本
     */
    private void runAgentLoop(String userMessage,
                              Consumer<String> onDelta,
                              Consumer<ToolCallInfo> onToolCall,
                              Consumer<ToolResultInfo> onToolResult);

    /**
     * 从流事件中收集一个完整响应的内容。
     * 返回 CollectedResponse，包含 text（完整文本）和 toolUses（工具调用列表）。
     * 在收集过程中实时回调 onDelta（文本）和 onToolCall（工具调用开始）。
     */
    private CollectedResponse collectStreamResponse(
        StreamResponse<RawMessageStreamEvent> stream,
        Consumer<String> onDelta,
        Consumer<ToolCallInfo> onToolCall);

    /**
     * 构建 MessageCreateParams（含 tools 列表）。
     */
    private MessageCreateParams buildParams(List<MessageParam> messages);
}
```

#### 新增辅助类型（ClaudeService 内部或独立文件）

```java
// 工具调用信息 -- 传给客户端用于 UI 展示
public record ToolCallInfo(String toolUseId, String toolName) {}

// 工具执行结果信息 -- 传给客户端用于 UI 展示  
public record ToolResultInfo(String toolUseId, String toolName, boolean success, String summary) {}

// 流式响应收集结果（内部使用）
record CollectedResponse(String text, List<PendingToolUse> toolUses) {}

// 一次待执行的工具调用（内部使用）
record PendingToolUse(String id, String name, String inputJson) {}
```

#### 关键 SDK 类型使用

| 操作 | SDK 类型 / 方法 |
|------|----------------|
| 定义工具 | `Tool.builder().name().description().inputSchema(Schema.builder()...).build()` |
| 传入 API | `MessageCreateParams.builder().tools(toolDefinitions)` |
| 检测 tool_use 开始 | `event.contentBlockStart().ifPresent(s -> s.contentBlock().toolUse().ifPresent(...))` |
| tool_use block 属性 | `ToolUseBlock.id()`, `.name()`, `.input()` (可能是 JSON 字符串) |
| 累积 tool_use input | `event.contentBlockDelta().ifPresent(d -> d.delta().inputJsonDelta().ifPresent(j -> j.partialJson()))` |
| 构建 assistant 消息（含 tool_use） | `MessageParam.assistant().content(ContentBlockParam.ofToolUse(...)).build()` |
| 构建 tool_result | `ContentBlockParam.ofToolResult(toolUseId, resultContent)` |

### 4.2 `cc/claw/api/ChatController.java` -- 适配新接口

改动较小：适配 ClaudeService 的新回调签名。

```java
@PostMapping("/chat")
public SseEmitter chat(@RequestBody ChatRequest request) {
    SseEmitter emitter = new SseEmitter(120000L); // 延长超时：tool loop 可能更长

    claudeService.streamMessage(
        request.message(),
        text -> sendEvent(emitter, "text", Map.of("delta", text)),
        toolCall -> sendEvent(emitter, "tool_call", Map.of(            // 新增
            "toolUseId", toolCall.toolUseId(),
            "toolName", toolCall.toolName()
        )),
        toolResult -> sendEvent(emitter, "tool_result", Map.of(       // 新增
            "toolUseId", toolResult.toolUseId(),
            "toolName", toolResult.toolName(),
            "success", toolResult.success(),
            "summary", toolResult.summary()
        )),
        error -> { /* 不变 */ },
        () -> { /* 不变 */ }
    );

    return emitter;
}
```

SSE 事件类型一览（Phase 1 新增 `tool_call`、`tool_result`）：

| 事件名 | 触发时机 | payload |
|--------|---------|---------|
| `text` | Claude 返回文本 delta | `{"delta": "..."}` |
| `tool_call` | Claude 发起工具调用 | `{"toolUseId":"...","toolName":"..."}` |
| `tool_result` | 工具执行完成 | `{"toolUseId":"...","toolName":"...","success":true,"summary":"..."}` |
| `error` | 任何错误 | `{"message": "..."}` |
| `done` | Agent Loop 结束 | `{}` |

### 4.3 `cc/claw/ClawConfig.java` -- 添加配置项

```java
@ConfigurationProperties(prefix = "claw")
public record ClawConfig(
    @DefaultValue("${user.home}/.c-claw") String home,
    @DefaultValue("19800") int bridgePort       // 新增：Electron System Bridge 端口
) {
    public Path homePath() { return Paths.get(home); }

    /** System Bridge 完整 URL：http://127.0.0.1:{bridgePort} */
    public String bridgeUrl() {
        return "http://127.0.0.1:" + bridgePort;
    }
}
```

### 4.4 `cc/claw/config/AnthropicConfig.java` -- 微小调整

无需改代码逻辑，仅确认 API Key 为空时给出更清晰的错误提示（Phase 0 已有即可）。

---

## 五、数据流详解

### 场景：「看看当前窗口是什么」

```
用户 POST /api/chat  {"message":"看看当前窗口是什么"}
    │
    ▼
ChatController
    │
    ▼
ClaudeService.streamMessage()
    │
    │  ┌─────────────────────────────────────────┐
    │  │           Agent Loop (Round 1)           │
    │  │                                         │
    │  │  messages = [User:"看看当前窗口是什么"]   │
    │  │  params = buildParams(messages, tools)  │
    │  │  stream = client.createStreaming(params) │
    │  │      ↓                                  │
    │  │  [content_block_start: tool_use]        │
    │  │      → onToolCall("window_watcher")     │
    │  │      → SSE: tool_call → 客户端          │
    │  │  [content_block_delta: input_json]      │
    │  │      → 累积: "{}"                       │
    │  │  [content_block_stop]                   │
    │  │  [message_stop: stop_reason=tool_use]   │
    │  │                                         │
    │  │  toolUses = [{id:"toolu_01", name:"window_watcher", input:"{}"}] │
    │  │                                         │
    │  │  messages += Assistant[tool_use(...)]    │
    │  │                                         │
    │  │  toolResult = toolExecutor.execute()    │
    │  │      → GET http://127.0.0.1:19800/api/window/current │
    │  │      → {"title":"Chrome - GitHub","process":"chrome.exe"} │
    │  │      → onToolResult(success, summary)   │
    │  │      → SSE: tool_result → 客户端        │
    │  │                                         │
    │  │  messages += User[tool_result(id,content)]│
    │  └─────────────────────────────────────────┘
    │
    │  ┌─────────────────────────────────────────┐
    │  │           Agent Loop (Round 2)           │
    │  │                                         │
    │  │  stream = client.createStreaming(params) │
    │  │      ↓                                  │
    │  │  [content_block_delta: text]            │
    │  │      → onDelta("当前窗口是...")          │
    │  │      → SSE: text → 客户端               │
    │  │  [message_stop: stop_reason=end_turn]   │
    │  │                                         │
    │  │  toolUses = []  → 循环结束              │
    │  └─────────────────────────────────────────┘
    │
    ▼
onComplete() → SSE: done → 客户端
```

### 消息累积规则

每轮循环后 messages 列表增长如下：

```
Round 0: [User: "看看当前窗口是什么"]
Round 1: [User: "...", Assistant: [tool_use(window_watcher, id=toolu_01, input={})], User: [tool_result(toolu_01, "{\"title\":\"Chrome\"}")]]
Round 2: [User: "...", Assistant: [...], User: [...], Assistant: [text: "当前窗口是 Chrome"]]
→ stop_reason=end_turn, 无 tool_use → 结束
```

### 多工具并行处理

Claude 可能在一个响应中返回多个 `tool_use` block（不同 `content_block`）。例如：

> Claude 同时调用 `window_watcher` 和 `clipboard_read`

此时这些工具调用应并行执行（`CompletableFuture.allOf`），全部完成后统一追加 assistant + tool_result 消息，然后继续下一轮。

```java
// 并行执行所有工具调用
List<CompletableFuture<ToolResult>> futures = pendingToolUses.stream()
    .map(tu -> toolExecutor.execute(tu.id(), tu.name(), tu.inputJson()))
    .toList();

CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

List<ToolResult> results = futures.stream()
    .map(CompletableFuture::join)
    .toList();
```

---

## 六、关键事件类型映射

由 Anthropic SDK `RawMessageStreamEvent` 的子类型解析：

| SDK 事件 | 提取方法 | 用途 |
|----------|---------|------|
| `content_block_start` | `.contentBlock().toolUse()` | 检测 tool_use 开始，获取 id 和 name |
| `content_block_start` | `.contentBlock().text()` | 检测 text block 开始（Phase 1 不特殊处理） |
| `content_block_delta` | `.delta().text().map(TextDelta::text)` | 文本 delta，转发给客户端 |
| `content_block_delta` | `.delta().inputJsonDelta().map(InputJsonDelta::partialJson)` | 工具参数 JSON delta，累积拼接 |
| `content_block_stop` | `.index()` | 某个 content block 结束 |
| `message_stop` | — | 整个消息结束，检查 stop_reason |
| `message_delta` | `.delta().stopReason()` | `"end_turn"` = 正常结束；`"tool_use"` = 需要继续循环 |

**注意**：`message_delta` 的 `stopReason` 需要在 `message_stop` 之后才能可靠获取。实现时在 `message_stop` 事件处检查是否有待执行的 tool_use 来决定是否继续循环。

---

## 七、错误处理

| 场景 | 处理方式 |
|------|----------|
| Claude API 返回 4xx/5xx | `onError` 回调，SSE `error` 事件，循环终止 |
| 工具执行超时（>30s） | `onToolResult(false, "timeout")`，tool_result 内容为错误信息，送入下一轮让 Claude 自行处理 |
| 工具执行 HTTP 失败 | `onToolResult(false, "HTTP 502: ...")`，同上 |
| 超过 MAX_TOOL_ROUNDS | 发送 SSE `text` 事件："抱歉，处理超时，请简化请求重试"，然后 `done` |
| 客户端断开连接 | SSE 抛出 IOException，由 SseEmitter.completeWithError 终止 |
| 用户消息为空 | 在 Controller 层校验 `@NotBlank`，400 返回 |
| clipboard_write 超大文本 | BuiltinToolExecutor 拒绝（>100KB），返回 error tool_result |

---

## 八、测试策略

### 单元测试

| 测试类 | 验证点 |
|--------|--------|
| `BuiltinToolDefinitionsTest` | 4 个工具定义结构正确，JSON Schema 可序列化 |
| `BuiltinToolExecutorTest` | mock RestTemplate，验证各工具的 HTTP 请求正确构造 |
| `ClaudeServiceTest` | mock AnthropicClient 流式响应，验证循环逻辑、工具分发、消息构建 |

### 集成测试

| 场景 | 验证点 |
|------|--------|
| 纯文本消息（无工具调用） | 文本 delta 正常流式返回，done 事件正常触发 |
| 单工具调用 | 先收到 tool_call 事件，再收到 tool_result 事件，然后 text delta，最后 done |
| 多工具调用（并行） | 多个 tool_call 事件连续，多个 tool_result 同步/异步返回，最终 text + done |
| 工具失败 | tool_result.success=false，Claude 能根据错误信息给出合理回复 |

### 手工验收（端到端）

通过 curl 或 Electron 前端发出请求，验证：
1. `curl -H "Content-Type: application/json" -d '{"message":"看看当前窗口"}' http://127.0.0.1:{port}/api/chat` 能看到 text/tool_call/tool_result/done 事件序列

---

## 九、文件变更汇总

| 操作 | 文件路径 | 说明 |
|------|---------|------|
| **新增** | `src/main/java/cc/claw/agent/tool/ToolDefinition.java` | 工具定义记录 |
| **新增** | `src/main/java/cc/claw/agent/tool/ToolResult.java` | 工具执行结果记录 |
| **新增** | `src/main/java/cc/claw/agent/tool/ToolExecutor.java` | 执行器接口 |
| **新增** | `src/main/java/cc/claw/agent/tool/BuiltinToolExecutor.java` | Phase 1 内置工具实现 |
| **新增** | `src/main/java/cc/claw/agent/tool/BuiltinToolDefinitions.java` | 4 个工具的静态定义 |
| **新增** | `src/main/java/cc/claw/agent/ToolCallInfo.java` | SSE 事件 DTO（tool_call） |
| **新增** | `src/main/java/cc/claw/agent/ToolResultInfo.java` | SSE 事件 DTO（tool_result） |
| **修改** | `src/main/java/cc/claw/agent/ClaudeService.java` | 核心改造：Agent Loop |
| **修改** | `src/main/java/cc/claw/api/ChatController.java` | 适配新回调 + 超时延长 |
| **修改** | `src/main/java/cc/claw/ClawConfig.java` | 新增 bridgePort 配置 |
| **新增** | `src/test/java/cc/claw/agent/tool/BuiltinToolDefinitionsTest.java` | 工具定义测试 |
| **新增** | `src/test/java/cc/claw/agent/tool/BuiltinToolExecutorTest.java` | 工具执行测试 |
| **新增** | `src/test/java/cc/claw/agent/ClaudeServiceTest.java` | Agent Loop 测试 |

---

## 十、实现顺序

```
Step 1: ToolDefinition + ToolResult + ToolCallInfo + ToolResultInfo
        （数据模型，无依赖）

Step 2: BuiltinToolDefinitions
        （依赖 ToolDefinition + Anthropic SDK Tool/Schema 类型）

Step 3: ToolExecutor 接口 + BuiltinToolExecutor
        （依赖 Step 1/2 + ClawConfig + RestTemplate）

Step 4: ClaudeService 改造
        （依赖 Step 2/3 + AnthropicClient）

Step 5: ChatController 适配
        （依赖 Step 4）

Step 6: ClawConfig 新增 bridgePort
        （独立改动，1 行）

Step 7: 单元测试 + 集成测试
```

---

## 十一、风险评估与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Anthropic SDK 类型与文档不符 | 编译失败 | Step 2 先单独验证 Tool/Schema builder API |
| 流式解析 tool_use 时 input_json 拼接顺序错乱 | tool 参数错误 | 按 content_block index 分组累积，不依赖 delta 顺序 |
| Electron System Bridge 尚未实现 | 工具调用返回 HTTP 错误 | ToolExecutor 将 HTTP 错误包装为 tool_result（而非崩溃），Claude 能自行容错 |
| MAX_TOOL_ROUNDS 设置不合理 | 过早截断或资源浪费 | 默认 10 轮，后续根据实际使用调整；超限时优雅降级 |
| SSE 超时不够 | 正常工具调用被截断 | 从 60s 延长到 120s |

---

## 十二、与 Phase 2 的衔接

| 本阶段设计 | Phase 2 扩展点 |
|-----------|---------------|
| `ToolExecutor` 接口 | Phase 2 新增 `SkillToolExecutor` 实现，从 skill YAML 读取工具定义 |
| `ToolDefinition` | Phase 2 增加 `source` 字段区分 `BUILTIN` / `SKILL` |
| `ClaudeService.runAgentLoop` | 不变 -- 循环逻辑与工具来源解耦 |
| `BuiltinToolExecutor` | Phase 2 不修改，专注新增 |
| SSE 事件 `tool_call` / `tool_result` | Phase 2 复用，前端 UI 统一渲染 |