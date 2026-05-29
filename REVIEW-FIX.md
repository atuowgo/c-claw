# Phase 0 Review Fix Report

**Fix Date:** 2026-05-28
**Iteration:** 1 (first pass)
**Findings Applied:** 5/5
**Findings Skipped:** 0/5

## Fix Summary

| ID | Status | Description | File(s) |
|----|--------|-------------|---------|
| WR-01 | Fixed | Java startup timeout cleared on success and exit | `JavaProcess.ts` |
| WR-02 | Fixed | CORS configuration for localhost and file:// origins | `WebConfig.java` (new) |
| WR-03 | Fixed | DOMPurify sanitization on v-html Markdown rendering | `MessageList.vue`, `package.json` |
| WR-04 | Fixed | Process reference captured to avoid null race in stop() | `JavaProcess.ts` |
| WR-05 | Fixed | Fail-fast IllegalStateException on missing API key | `AnthropicConfig.java` |

## Verification

### Electron App (npm run build)

All 3 bundles built without errors:
- main/index.js (8.54 kB) -- 5 modules
- preload/index.js (0.22 kB) -- 1 module
- renderer (index.html + CSS + JS 336.34 kB) -- 38 modules (was 31)

### Java Backend (mvn compile)

`mvn` not available on this build machine. `javac` syntax check confirms compilation errors are exclusively missing classpath dependencies (Spring Boot 3.4.1, anthropic-java 2.34.1) -- zero syntax or type errors in modified files. Full Maven compilation requires `mvn compile` in the target environment.

### Detail per Finding

**WR-01 (JavaProcess.ts:start)**
- `clearTimeout(startupTimeout)` added in `.then()` success handler (line 62)
- `clearTimeout(startupTimeout)` added in `.catch()` error handler (line 67)
- `clearTimeout(startupTimeout)` added in `process.on('exit')` handler (line 54)
- `reject` pass now uses explicit `err => { clearTimeout(...); reject(err); }` instead of bare `reject`

**WR-02 (WebConfig.java)**
- New file: `java-backend/src/main/java/cc/claw/config/WebConfig.java`
- Implements `WebMvcConfigurer` with `addCorsMappings`
- Allows `http://localhost:*` and `file://` origins for `/api/**` endpoints
- `allowedOriginPatterns` compatible with Spring Boot 3.4.1 (uses Spring Framework 6.x)

**WR-03 (MessageList.vue)**
- `dompurify` added to `package.json` dependencies (`^3.2.6`) and installed
- Import `DOMPurify from 'dompurify'` added to `<script setup>`
- `renderContent()` now sanitizes markdown output: `DOMPurify.sanitize(raw)` before return
- User messages continue to use `escapeHtml()` (unchanged)

**WR-04 (JavaProcess.ts:stop)**
- Process reference captured at function start: `const proc = this.process`
- `forceKillTimeout` callback uses `proc.kill('SIGKILL')` with try/catch
- `proc.on('exit')` uses captured reference
- `proc.kill('SIGTERM')` uses captured reference with try/catch

**WR-05 (AnthropicConfig.java)**
- `if (apiKey == null || apiKey.isBlank())` now throws `IllegalStateException` with message:
  "ANTHROPIC_API_KEY environment variable is not set. Please set it before starting C-Claw."
- Removed "placeholder" fallback logic

## Files Modified

1. `electron-app/src/main/java-launcher/JavaProcess.ts` (WR-01, WR-04)
2. `electron-app/src/renderer/components/MessageList.vue` (WR-03)
3. `electron-app/package.json` (WR-03 -- dompurify dependency)
4. `java-backend/src/main/java/cc/claw/config/WebConfig.java` (WR-02 -- new file)
5. `java-backend/src/main/java/cc/claw/config/AnthropicConfig.java` (WR-05)