---
reviewed: "2026-06-01T00:00:00Z"
depth: standard
project: C-Claw Phase 1
files_reviewed_list:
  - "java-backend/src/main/java/cc/claw/agent/ClaudeService.java"
  - "java-backend/src/main/java/cc/claw/agent/tool/BuiltinToolExecutor.java"
  - "java-backend/src/main/java/cc/claw/agent/tool/BuiltinToolDefinitions.java"
  - "java-backend/src/main/java/cc/claw/api/ChatController.java"
  - "electron-app/src/main/bridge/BridgeServer.ts"
  - "electron-app/src/main/bridge/WindowWatcher.ts"
  - "electron-app/src/main/bridge/ClipboardWatcher.ts"
  - "electron-app/src/main/bridge/ShortcutManager.ts"
  - "java-backend/src/main/java/cc/claw/ClawConfig.java"
  - "java-backend/src/main/java/cc/claw/skill/SkillRegistry.java"
  - "java-backend/src/main/java/cc/claw/memory/MemoryStore.java"
status: issues_found
---

# Phase 1: Code Review Report

**Reviewed:** 2026-06-01T00:00:00Z
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

Phase 1 agent-loop and system-bridge implementation reviewed. Overall architecture is sound: the ClaudeService agent loop correctly implements the streaming tool_use pattern, BuiltinToolExecutor bridges to Electron via HTTP, and BridgeServer exposes desktop capabilities.

4 critical issues found: URL injection in BridgeServer tool executor, silent JSON parse failure in agent loop, LIKE pattern injection in MemoryStore, and clipboard state drift on write failure. 5 warnings and 3 info items identified.

## Critical Issues

### CR-01: URL Injection in BuiltinToolExecutor.executeWindowWatcher

**File:** `java-backend/src/main/java/cc/claw/agent/tool/BuiltinToolExecutor.java:71`
**Issue:** `processName` argument from tool input is concatenated directly into a URL query string without encoding. Special characters (`&`, `=`, `#`, spaces) will produce malformed URLs or be interpreted as additional query parameters.
**Fix:**

```java
// Replace line 70-71 with:
String processName = args.get("processName").asText();
url += "?processName=" + java.net.URLEncoder.encode(processName, StandardCharsets.UTF_8);
```

### CR-02: Silent JSON Parse Failure Produces Invalid Tool Input

**File:** `java-backend/src/main/java/cc/claw/agent/ClaudeService.java:170-177`
**Issue:** When `objectMapper.readTree(tu.inputJson())` throws, the catch block silently swallows the error and builds a `ToolUseBlockParam` with an empty input. The next API call then receives a malformed tool_use block, causing an Anthropic API error (`RuntimeException`) with no useful diagnostics about the root cause.
**Fix:** Re-throw as a descriptive exception, or skip the failing tool call and inject an error `ToolResultBlockParam` directly:

```java
try {
    JsonNode root = objectMapper.readTree(tu.inputJson());
    // ... populate inputBuilder ...
} catch (Exception e) {
    log.error("Failed to parse tool input JSON for tool {}: {}", tu.name(), tu.inputJson(), e);
    throw new RuntimeException("Tool input JSON parse failure for tool: " + tu.name(), e);
}
```

### CR-03: LIKE Pattern Injection in MemoryStore.searchMemories

**File:** `java-backend/src/main/java/cc/claw/memory/MemoryStore.java:186-191`
**Issue:** User-supplied `query` string is embedded in a `LIKE` pattern via PreparedStatement parameter. SQL injection is prevented by parameterization, but `LIKE` special characters (`%`, `_`) in the query are interpreted as wildcards, not literals. A query like `50%` matches all memories, not just those containing `50%`.
**Fix:**

```java
private String escapeLike(String input) {
    return input.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
}

// In searchMemories, line 186:
String likeQuery = "%" + escapeLike(query) + "%";
```

### CR-04: ClipboardWatcher Internal State Drifts on writeContent Failure

**File:** `electron-app/src/main/bridge/ClipboardWatcher.ts:57-61`
**Issue:** `writeContent()` sets `this.lastContent = content` **before** calling `clipboard.writeText(content)`. If `writeText` throws (e.g., Electron security policy blocks clipboard access), `lastContent` is already updated. The next polling interval will read the real (unchanged) clipboard, but no `change` event fires because the comparison `current !== this.lastContent` sees a match against the cached-but-never-written value.
**Fix:**

```typescript
writeContent(content: string): void {
    clipboard.writeText(content)
    this.lastContent = content
    this.lastTimestamp = Date.now()
}
```

## Warnings

### WR-01: onComplete Callback Failure Leaves SSE Emitter Dangling

**File:** `java-backend/src/main/java/cc/claw/api/ChatController.java:48-51`
**Issue:** If `onComplete.run()` throws an exception, the `catch (Exception e)` block in `streamMessage` never catches it (it only wraps `runAgentLoop`), so `emitter.complete()` is never called and the SSE connection leaks until the 120s timeout.
**Fix:**

```java
() -> {
    try {
        sendEvent(emitter, "done", Map.of());
        emitter.complete();
    } catch (Exception e) {
        log.error("Failed to complete SSE", e);
    }
}
```

### WR-02: ShortcutManager Listener Leak (No Unsubscribe)

**File:** `electron-app/src/main/bridge/ShortcutManager.ts:10-14`
**Issue:** `listeners` array grows unbounded. There is no `offTrigger` mechanism. BridgeServer does not currently call `onTrigger`, but any future consumer that does will leak listeners if the consumer lifecycle ends.
**Fix:** Add a removal method:

```typescript
onTrigger(callback: (data: { key: string; action: string }) => void): () => void {
    this.listeners.push(callback)
    return () => {
        const idx = this.listeners.indexOf(callback)
        if (idx >= 0) this.listeners.splice(idx, 1)
    }
}
```

### WR-03: BridgeServer.stop Does Not Clean Up Port File

**File:** `electron-app/src/main/bridge/BridgeServer.ts:125-139`
**Issue:** `stop()` clears `bridgePort = null` but does not delete `~/.c-claw/bridge.port`. If the Electron process restarts after a crash where `stop()` was called cleanly, `ClawConfig.resolveBridgePort()` may read a stale port from the file before the new `start()` overwrites it.
**Fix:**

```typescript
// In stop(), after server.close():
const configDir = path.join(os.homedir(), '.c-claw')
const portFile = path.join(configDir, 'bridge.port')
if (fs.existsSync(portFile)) {
    fs.unlinkSync(portFile)
}
```

### WR-04: RestTemplate Uses Infinite Timeout

**File:** `java-backend/src/main/java/cc/claw/agent/tool/BuiltinToolExecutor.java:32`
**Issue:** `new RestTemplate()` is created with default `SimpleClientHttpRequestFactory`, which has `connectTimeout` and `readTimeout` set to -1 (infinite). If the Electron bridge is unresponsive, tool execution threads block indefinitely.
**Fix:**

```java
var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
factory.setConnectTimeout(5000);
factory.setReadTimeout(10000);
this.restTemplate = new RestTemplate(factory);
```

### WR-05: WindowWatcher.getActiveWindow Silent Catch

**File:** `electron-app/src/main/bridge/WindowWatcher.ts:18-19`
**Issue:** The `catch {}` block is completely empty. If `active-win` fails (permission denied on Wayland, missing native module), consumers get `null` with no diagnostic information anywhere, making troubleshooting difficult.
**Fix:**

```typescript
} catch (err) {
    console.error('[c-claw] WindowWatcher error:', err)
    return null
}
```

## Info

### IN-01: ClawConfig.bridgeUrl Reads Port File on Every Call

**File:** `java-backend/src/main/java/cc/claw/ClawConfig.java:29-40`
**Issue:** `resolveBridgePort()` reads `bridge.port` from disk each time `bridgeUrl()` is called (every tool execution round). Not a correctness issue, but introduces unnecessary IO for a value that rarely changes during a session.
**Fix:** Cache the resolved port after first read, or read once in a `@PostConstruct` method.

### IN-02: ClipboardWatcher Singleton Constructor Leaks EventEmitter on Extra Instantiation

**File:** `electron-app/src/main/bridge/ClipboardWatcher.ts:17-21`
**Issue:** The singleton pattern in the constructor calls `super()` (creating an EventEmitter) before `return instance` bypasses `this`. On each extra `new ClipboardWatcher()` call, one EventEmitter object is created and immediately garbage collected. Currently only instantiated once in BridgeServer.ts, so this is dormant.
**Fix:** Use a static factory or module-level singleton export instead of constructor interception.

### IN-03: ShortcutManager.emit Has Dead event Parameter

**File:** `electron-app/src/main/bridge/ShortcutManager.ts:16-20`
**Issue:** `emit(event: string, data: ...)` checks `if (event === 'trigger')` but the method is only ever called with the literal string `'trigger'`. The `event` parameter and the conditional are dead code.
**Fix:** Remove the `event` parameter or simplify to a direct listener invocation.

---

_Reviewed: 2026-06-01T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_