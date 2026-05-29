# Phase 0 Code Review Report

**Reviewed:** 2026-05-28
**Depth:** standard
**Files Reviewed:** 20
**Status:** issues_found

## Summary

Phase 0 provides a functional but fragile foundation. The Electron-Java architecture with IPC context isolation is well-designed. Key concerns center on the SSE streaming parser (which can silently drop data under TCP fragmentation), missing CORS configuration (breaking dev-mode chat), resource cleanup gaps in the Java process lifecycle, and absent HTML sanitization for Claude API responses. No blocking security vulnerabilities were found in the IPC bridge or API key handling, though several defense-in-depth improvements are recommended.

## Critical Issues

### CR-01: SSE parser state is lost across read() chunks

**File:** `electron-app/src/renderer/stores/chat.ts:71-103`
**Issue:** The `currentEvent` variable is declared as a local `let` inside the outer `while(true)` loop body (line 81). Each call to `reader.read()` begins a new iteration, resetting `currentEvent` to `''`. If the TCP stream fragments an SSE event such that the `event: text\n` line arrives in one `read()` call and the `data: ...` line arrives in the next, the data line is silently discarded because `currentEvent` is empty when the data line is parsed.

**Scenario to reproduce:**
1. Chunk 1 arrives: `"event: text\n"` -- `currentEvent` is set to `"text"`, but is discarded at loop end
2. Chunk 2 arrives: `"data: {\"delta\":\"hello\"}\n\n"` -- `currentEvent` is `''`, the data is ignored
3. The assistant bubble shows no streaming text -- silent data loss

While localhost TCP rarely fragments small payloads, HTTP/1.1 chunked encoding and OS socket buffer pressure can trigger this, making it a non-deterministic bug.

**Fix:** Hoist `currentEvent` outside the while loop and reset it only on blank lines (the SSE event delimiter):

```typescript
let buffer = ''
let currentEvent = ''

while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    for (const line of lines) {
        if (line === '' || line === '\r') {
            currentEvent = ''
            continue
        }
        if (line.startsWith('event: ')) {
            currentEvent = line.slice(7).trim()
        } else if (line.startsWith('data: ')) {
            const data = line.slice(6)
            if (currentEvent === 'text') {
                try {
                    const parsed = JSON.parse(data)
                    assistantMsg.content += parsed.delta || ''
                } catch {}
            } else if (currentEvent === 'error') {
                try {
                    const parsed = JSON.parse(data)
                    error.value = parsed.message || 'Unknown error'
                } catch {
                    error.value = 'Unknown error'
                }
            }
        }
    }
}
```

### CR-02: SSE parser does not handle blank-line event delimiters

**File:** `electron-app/src/renderer/stores/chat.ts:81-103`
**Issue:** The SSE protocol uses a blank line (`\n\n`) to delimit events. The parser splits on `\n` but takes no action on blank lines: `currentEvent` is never reset. This means if two successive events arrive with no blank line handling:
- Event 1: `text` -- processed correctly
- (blank line ignored)
- Event 2: `done` with a data line -- could match `currentEvent === 'text'` if previous event was `text` and the blank line was skipped

This can cause a `done` event's data to be incorrectly routed to the `text` handler.

**Fix:** Same as CR-01 -- add explicit blank-line handling that resets `currentEvent`.

## Warnings

### WR-01: Java startup timeout never cleared on success

**File:** `electron-app/src/main/java-launcher/JavaProcess.ts:59-69`
**Issue:** The `startupTimeout` set at line 67 is only cleared in the `process.on('error')` handler (line 48). The success path in `waitForReady().then(...)` (line 59-62) does NOT call `clearTimeout(startupTimeout)`. This means:
1. The timer handle leaks for the lifetime of the app (30-second timer remains in the event loop)
2. If the promise had somehow not resolved (edge case), the stale timeout could fire later

Additionally, the `process.on('exit')` handler (line 52-56) does not clear the startup timeout either.

**Fix:**
```typescript
startupTimeout = setTimeout(() => {
    reject(new Error('Java backend startup timed out (30s)'))
}, 30000)

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
```

Also add `clearTimeout(startupTimeout)` in the `process.on('exit')` handler at line 52.

### WR-02: No CORS configuration -- chat broken in dev mode

**File:** `java-backend/src/main/resources/application.yml` and `java-backend/src/main/java/cc/claw/api/ChatController.java`
**Issue:** The Spring Boot backend binds to `127.0.0.1` only (good for security) but has zero CORS configuration. In development mode, `electron-vite` serves the renderer from `http://localhost:5173` (a different origin from `http://127.0.0.1:{port}`). The browser enforces CORS for cross-origin `fetch()` calls, so `POST /api/chat` will be blocked with a CORS error. The user will see "Failed to send message" with no clear indication that CORS is the root cause.

This also affects production when the renderer is loaded from `file://` -- Chromium may block the cross-origin request depending on version and flags.

**Fix:** Add a `WebMvcConfigurer` bean or `@CrossOrigin` annotation for development. Since this is a local-only desktop app, allowing origin `http://localhost:*` in dev and `null` for file:// in production is appropriate:

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns("http://localhost:*", "file://")
            .allowedMethods("*");
    }
}
```

### WR-03: `v-html` renders unsanitized Markdown -- XSS vector

**File:** `electron-app/src/renderer/components/MessageList.vue:12`
**Issue:** Line 12 uses `v-html="renderContent(msg)"` to inject the Claude API response directly into the DOM. The `marked` library can produce raw HTML including `<script>`, `<iframe>`, event handlers (`onerror`, `onload`), and other active content. If the LLM response contains malicious content (via prompt injection or a compromised model endpoint), it would execute in the renderer process.

While `contextIsolation: true` and `nodeIntegration: false` limit the blast radius to the renderer sandbox, a renderer XSS can still:
- Exfiltrate chat history via `fetch()` to external servers
- Phish the user by modifying the DOM
- Exploit any Electron renderer-level CVEs

**Fix:** Use a sanitization library like DOMPurify before injecting:

```typescript
import DOMPurify from 'dompurify'

function renderContent(msg: Message): string {
    if (msg.role === 'user') return escapeHtml(msg.content)
    try {
        const raw = marked.parse(msg.content, { async: false }) as string
        return DOMPurify.sanitize(raw)
    } catch {
        return escapeHtml(msg.content)
    }
}
```

### WR-04: Java process stop() hangs 10s if process already exited

**File:** `electron-app/src/main/java-launcher/JavaProcess.ts:142-164`
**Issue:** The `stop()` method sets an `exit` listener and sends SIGTERM. If the Java process has already exited (crashed, or was killed externally) before `stop()` is called:
1. `this.process` is not null (it was never cleared, because the exit handler at line 52 sets `this.process = null` but only if the process emits `exit` before `stop()` is called -- there's a race)
2. The `process.on('exit')` listener will never fire because the process already exited
3. The promise only resolves after the 10-second `forceKillTimeout`

Wait, actually if the process already exited, `this.process` would be null because the `exit` listener at line 52 sets `this.process = null`. So `if (!this.process) return` at line 143 would short-circuit. This is fine.

However, there's another scenario: the process is in the middle of exiting when `stop()` is called. The `exit` listener at line 52 fires, setting `this.process = null`. Then `this.process.kill('SIGTERM')` at line 163 would throw because `this.process` is null (the listener check at 143 captured a non-null reference, but the `exit` event handler nulled it). Actually, line 156 captures `this.process!` in a closure -- but line 163 uses `this.process!` directly, which could be null by then.

**Fix:** Capture the process reference at the start and check it:
```typescript
async stop(): Promise<void> {
    const proc = this.process
    if (!proc) return

    return new Promise(resolve => {
        const forceKillTimeout = setTimeout(() => {
            try { proc.kill('SIGKILL') } catch {}
            resolve()
        }, 10000)

        proc.on('exit', () => {
            clearTimeout(forceKillTimeout)
            resolve()
        })

        try { proc.kill('SIGTERM') } catch {}
    })
}
```

### WR-05: AnthropicConfig falls back to "placeholder" -- no clear error for missing API key

**File:** `java-backend/src/main/java/cc/claw/config/AnthropicConfig.java:13-17`
**Issue:** When `ANTHROPIC_API_KEY` is not set, the code uses `"placeholder"` as the API key. Every chat request will then fail with an HTTP 401/403 from the Anthropic API, producing a confusing error in the chat UI ("Server error: 500" or "Unknown error"). The user has no indication that the missing environment variable is the root cause.

**Fix:** Fail fast with a clear message:
```java
@Bean
public AnthropicClient anthropicClient() {
    String apiKey = System.getenv("ANTHROPIC_API_KEY");
    if (apiKey == null || apiKey.isBlank()) {
        throw new IllegalStateException(
            "ANTHROPIC_API_KEY environment variable is not set. " +
            "Please set it before starting C-Claw."
        );
    }
    return AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .build();
}
```

## Info

### IN-01: No AbortController for fetch -- SSE connection persists on unmount

**File:** `electron-app/src/renderer/stores/chat.ts:57`
**Issue:** The `fetch()` call for SSE streaming has no `AbortController`. If the user navigates away or the Vue component unmounts during an active stream, the connection remains open and the reader loop continues consuming resources. While the Pinia store persists across component mounts, if `clearChat()` is called mid-stream, the assistant message reference (`assistantMsg`) still exists and the `while(true)` loop keeps appending to a message that's no longer in the array.

**Fix:** Store an `AbortController` and abort on `clearChat()` or a new `cancelStream()` method.

### IN-02: `parseSseLine` in api/chat.ts is unused dead code

**File:** `electron-app/src/renderer/api/chat.ts:9-17`
**Issue:** The `parseSseLine` function is exported but never imported by any file. The actual SSE parsing is done inline in `stores/chat.ts`. This is dead code that adds confusion about where SSE parsing actually happens.

**Fix:** Either remove `api/chat.ts` or move the SSE parsing logic there and import it from the store.

### IN-03: CSS class `.error-banner` defined but uses hardcoded color values

**File:** `electron-app/src/renderer/components/MessageList.vue:188-195`
**Issue:** The error banner uses hardcoded `rgba(255, 68, 68, 0.13)` and `#ff4444` instead of CSS custom properties like `var(--error-color)`. This is inconsistent with the rest of the stylesheet which uses design tokens.

### IN-04: Port file path duplicated across TypeScript and Java

**File:** `electron-app/src/main/java-launcher/JavaProcess.ts:13` and `java-backend/src/main/java/cc/claw/ClawApplication.java:35`
**Issue:** The port file path `{home}/.c-claw/port` is hardcoded in both codebases. If one side changes the path, the other breaks silently. This is a synchronization risk.

**Fix:** Consider having the Java backend print the port to stdout in a structured format (e.g., `[c-claw] PORT=12345`) and have the Electron side parse stdout instead of polling a file. This eliminates the port file entirely.

### IN-05: Claude model string hardcoded

**File:** `java-backend/src/main/java/cc/claw/agent/ClaudeService.java:41`
**Issue:** The model `"claude-sonnet-4-20250514"` is a hardcoded string. Model identifiers change with new releases and API deprecations. Updating requires a code change and rebuild.

**Fix:** Move the model identifier to `application.yml` under a `claw.model` property, so it can be changed via external configuration.

### IN-06: Chat store `generateId()` uses non-cryptographic random

**File:** `electron-app/src/renderer/stores/chat.ts:27-29`
**Issue:** `Math.random()` is used for message ID generation. While message IDs don't require cryptographic security in this context, `crypto.randomUUID()` is available in modern Chromium (Electron 33+) and provides guaranteed uniqueness with no real cost.

### IN-07: System tray icon not destroyed on app quit

**File:** `electron-app/src/main/tray.ts:6-44`
**Issue:** The `tray` variable is set on creation but never set to null or destroyed. On `before-quit`, there's no `tray.destroy()` call. On Windows this can leave the tray icon visible until the user hovers over it.

### IN-08: SseEmitter callbacks may race with IOException from client disconnect

**File:** `java-backend/src/main/java/cc/claw/api/ChatController.java:47-54`
**Issue:** If `emitter.send()` in `sendEvent()` throws `IOException` (client disconnected), `emitter.completeWithError(e)` is called. Meanwhile, the async executor in `ClaudeService` may still be running and will later invoke the `onComplete` or `onError` callback, which will call `sendEvent()` again on the already-completed emitter. Spring's `SseEmitter` handles this gracefully (subsequent sends are silently ignored), but the `completeWithError` path could mask real errors.

### IN-09: No Content-Security-Policy meta tag

**File:** `electron-app/src/renderer/index.html`
**Issue:** The HTML file has no `<meta http-equiv="Content-Security-Policy">` tag. A CSP would add defense-in-depth against XSS in the renderer, particularly important given the `v-html` usage in MessageList.vue.

---

_Reviewed: 2026-05-28_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_