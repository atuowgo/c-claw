package cc.claw.agent.tool;

import cc.claw.ClawConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Phase 1 builtin tool executor.
 * Executes tools via HTTP calls to the Electron System Bridge.
 */
@Component
public class BuiltinToolExecutor implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(BuiltinToolExecutor.class);
    private static final int MAX_CLIPBOARD_BYTES = 100 * 1024;

    private final RestTemplate restTemplate;
    private final String bridgeUrl;
    private final List<ToolDefinition> tools;
    private final ObjectMapper objectMapper;

    public BuiltinToolExecutor(ClawConfig config) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
        this.bridgeUrl = config.bridgeUrl();
        this.tools = BuiltinToolDefinitions.all();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<ToolDefinition> getToolDefinitions() {
        return tools;
    }

    @Override
    public boolean canExecute(String toolName) {
        return tools.stream().anyMatch(t -> t.name().equals(toolName));
    }

    @Override
    public CompletableFuture<ToolResult> execute(String toolUseId, String toolName, String arguments) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return switch (toolName) {
                    case "window_watcher" -> executeWindowWatcher(toolUseId, arguments);
                    case "clipboard_read" -> executeClipboardRead(toolUseId);
                    case "clipboard_write" -> executeClipboardWrite(toolUseId, arguments);
                    case "shortcut_register" -> executeShortcutRegister(toolUseId, arguments);
                    case "desktop_screenshot" -> executeDesktopScreenshot(toolUseId);
                    case "desktop_get_windows" -> executeDesktopGetWindows(toolUseId);
                    case "desktop_focus_window" -> executeDesktopFocusWindow(toolUseId, arguments);
                    case "desktop_screen_info" -> executeDesktopScreenInfo(toolUseId);
                    case "file_read" -> executeFileRead(toolUseId, arguments);
                    case "file_write" -> executeFileWrite(toolUseId, arguments);
                    case "file_list" -> executeFileList(toolUseId, arguments);
                    case "file_search" -> executeFileSearch(toolUseId, arguments);
                    case "file_info" -> executeFileInfo(toolUseId, arguments);
                    case "browser_navigate" -> executeBrowserNavigate(toolUseId, arguments);
                    case "browser_get_content" -> executeBrowserGetContent(toolUseId);
                    case "browser_click" -> executeBrowserClick(toolUseId, arguments);
                    case "browser_type" -> executeBrowserType(toolUseId, arguments);
                    case "browser_screenshot" -> executeBrowserScreenshot(toolUseId);
                    case "browser_execute" -> executeBrowserExecute(toolUseId, arguments);
                    default -> ToolResult.failure(toolUseId, toolName, "Unknown tool: " + toolName);
                };
            } catch (Exception e) {
                log.error("Tool execution failed: {} - {}", toolName, e.getMessage());
                return ToolResult.failure(toolUseId, toolName, e.getMessage());
            }
        });
    }

    private ToolResult executeWindowWatcher(String toolUseId, String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            String url = bridgeUrl + "/bridge/window/active";
            if (args.has("processName") && !args.get("processName").isNull()) {
                url += "?processName=" + URLEncoder.encode(args.get("processName").asText(), StandardCharsets.UTF_8);
            }
            String result = restTemplate.getForObject(url, String.class);
            return ToolResult.success(toolUseId, "window_watcher", result != null ? result : "{}");
        } catch (Exception e) {
            return ToolResult.failure(toolUseId, "window_watcher", "Bridge error: " + e.getMessage());
        }
    }

    private ToolResult executeClipboardRead(String toolUseId) {
        try {
            String result = restTemplate.getForObject(bridgeUrl + "/bridge/clipboard", String.class);
            return ToolResult.success(toolUseId, "clipboard_read", result != null ? result : "");
        } catch (Exception e) {
            return ToolResult.failure(toolUseId, "clipboard_read", "Bridge error: " + e.getMessage());
        }
    }

    private ToolResult executeClipboardWrite(String toolUseId, String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            if (!args.has("content")) {
                return ToolResult.failure(toolUseId, "clipboard_write", "Missing required parameter 'content'");
            }
            String text = args.get("content").asText();
            if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_CLIPBOARD_BYTES) {
                return ToolResult.failure(toolUseId, "clipboard_write",
                    "Text exceeds maximum size of " + (MAX_CLIPBOARD_BYTES / 1024) + "KB");
            }
            Map<String, String> body = Map.of("content", text);
            restTemplate.postForObject(bridgeUrl + "/bridge/clipboard", body, String.class);
            return ToolResult.success(toolUseId, "clipboard_write", "{\"written\":true}");
        } catch (Exception e) {
            return ToolResult.failure(toolUseId, "clipboard_write", "Bridge error: " + e.getMessage());
        }
    }

    private ToolResult executeShortcutRegister(String toolUseId, String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            if (!args.has("key") || !args.has("action")) {
                return ToolResult.failure(toolUseId, "shortcut_register",
                    "Missing required parameters 'key' and/or 'action'");
            }
            Map<String, String> body = Map.of(
                "key", args.get("key").asText(),
                "action", args.get("action").asText()
            );
            restTemplate.postForObject(bridgeUrl + "/bridge/shortcut", body, String.class);
            return ToolResult.success(toolUseId, "shortcut_register", "{\"registered\":true}");
        } catch (Exception e) {
            return ToolResult.failure(toolUseId, "shortcut_register", "Bridge error: " + e.getMessage());
        }
    }

    // -- Desktop tools --

    private ToolResult executeDesktopScreenshot(String toolUseId) {
        try {
            String result = restTemplate.getForObject(bridgeUrl + "/bridge/desktop/screenshot", String.class);
            return ToolResult.success(toolUseId, "desktop_screenshot", result != null ? result : "{}");
        } catch (Exception e) {
            return ToolResult.failure(toolUseId, "desktop_screenshot", "Bridge error: " + e.getMessage());
        }
    }

    private ToolResult executeDesktopGetWindows(String toolUseId) {
        try {
            String result = restTemplate.getForObject(bridgeUrl + "/bridge/desktop/windows", String.class);
            return ToolResult.success(toolUseId, "desktop_get_windows", result != null ? result : "[]");
        } catch (Exception e) {
            return ToolResult.failure(toolUseId, "desktop_get_windows", "Bridge error: " + e.getMessage());
        }
    }

    private ToolResult executeDesktopFocusWindow(String toolUseId, String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            if (!args.has("idOrName")) {
                return ToolResult.failure(toolUseId, "desktop_focus_window",
                    "Missing required parameter 'idOrName'");
            }
            Map<String, String> body = Map.of("idOrName", args.get("idOrName").asText());
            restTemplate.postForObject(bridgeUrl + "/bridge/desktop/focus", body, String.class);
            return ToolResult.success(toolUseId, "desktop_focus_window", "{\"focused\":true}");
        } catch (Exception e) {
            return ToolResult.failure(toolUseId, "desktop_focus_window", "Bridge error: " + e.getMessage());
        }
    }

    private ToolResult executeDesktopScreenInfo(String toolUseId) {
        try {
            String result = restTemplate.getForObject(bridgeUrl + "/bridge/desktop/screen-info", String.class);
            return ToolResult.success(toolUseId, "desktop_screen_info", result != null ? result : "{}");
        } catch (Exception e) {
            return ToolResult.failure(toolUseId, "desktop_screen_info", "Bridge error: " + e.getMessage());
        }
    }

    // -- File tools --

    private ToolResult executeFileRead(String toolUseId, String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            if (!args.has("path")) {
                return ToolResult.failure(toolUseId, "file_read", "Missing required parameter 'path'");
            }
            String path = args.get("path").asText();
            StringBuilder url = new StringBuilder(bridgeUrl)
                .append("/bridge/fs/read?path=")
                .append(URLEncoder.encode(path, StandardCharsets.UTF_8));
            if (args.has("maxBytes") && !args.get("maxBytes").isNull()) {
                url.append("&maxBytes=").append(args.get("maxBytes").asInt());
            }
            String result = restTemplate.getForObject(url.toString(), String.class);
            return ToolResult.success(toolUseId, "file_read", result != null ? result : "");
        } catch (Exception e) {
            return ToolResult.failure(toolUseId, "file_read", "Bridge error: " + e.getMessage());
        }
    }

    private ToolResult executeFileWrite(String toolUseId, String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            if (!args.has("path") || !args.has("content")) {
                return ToolResult.failure(toolUseId, "file_write",
                    "Missing required parameters 'path' and/or 'content'");
            }
            Map<String, String> body = Map.of(
                "path", args.get("path").asText(),
                "content", args.get("content").asText()
            );
            restTemplate.postForObject(bridgeUrl + "/bridge/fs/write", body, String.class);
            return ToolResult.success(toolUseId, "file_write", "{\"written\":true}");
        } catch (Exception e) {
            return ToolResult.failure(toolUseId, "file_write", "Bridge error: " + e.getMessage());
        }
    }

    private ToolResult executeFileList(String toolUseId, String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            if (!args.has("path")) {
                return ToolResult.failure(toolUseId, "file_list", "Missing required parameter 'path'");
            }
            String url = bridgeUrl + "/bridge/fs/list?path="
                + URLEncoder.encode(args.get("path").asText(), StandardCharsets.UTF_8);
            String result = restTemplate.getForObject(url, String.class);
            return ToolResult.success(toolUseId, "file_list", result != null ? result : "[]");
        } catch (Exception e) {
            return ToolResult.failure(toolUseId, "file_list", "Bridge error: " + e.getMessage());
        }
    }

    private ToolResult executeFileSearch(String toolUseId, String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            if (!args.has("path") || !args.has("pattern")) {
                return ToolResult.failure(toolUseId, "file_search",
                    "Missing required parameters 'path' and/or 'pattern'");
            }
            String url = bridgeUrl + "/bridge/fs/search?path="
                + URLEncoder.encode(args.get("path").asText(), StandardCharsets.UTF_8)
                + "&pattern=" + URLEncoder.encode(args.get("pattern").asText(), StandardCharsets.UTF_8);
            String result = restTemplate.getForObject(url, String.class);
            return ToolResult.success(toolUseId, "file_search", result != null ? result : "[]");
        } catch (Exception e) {
            return ToolResult.failure(toolUseId, "file_search", "Bridge error: " + e.getMessage());
        }
    }

    private ToolResult executeFileInfo(String toolUseId, String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            if (!args.has("path")) {
                return ToolResult.failure(toolUseId, "file_info", "Missing required parameter 'path'");
            }
            String url = bridgeUrl + "/bridge/fs/info?path="
                + URLEncoder.encode(args.get("path").asText(), StandardCharsets.UTF_8);
            String result = restTemplate.getForObject(url, String.class);
            return ToolResult.success(toolUseId, "file_info", result != null ? result : "{}");
        } catch (Exception e) {
            return ToolResult.failure(toolUseId, "file_info", "Bridge error: " + e.getMessage());
        }
    }

    // -- Browser tools --

    private ToolResult executeBrowserNavigate(String toolUseId, String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            if (!args.has("url")) {
                return ToolResult.failure(toolUseId, "browser_navigate", "Missing required parameter 'url'");
            }
            Map<String, String> body = Map.of("url", args.get("url").asText());
            restTemplate.postForObject(bridgeUrl + "/bridge/browser/navigate", body, String.class);
            return ToolResult.success(toolUseId, "browser_navigate", "{\"navigated\":true}");
        } catch (Exception e) {
            return ToolResult.failure(toolUseId, "browser_navigate", "Bridge error: " + e.getMessage());
        }
    }

    private ToolResult executeBrowserGetContent(String toolUseId) {
        try {
            String result = restTemplate.getForObject(bridgeUrl + "/bridge/browser/content", String.class);
            return ToolResult.success(toolUseId, "browser_get_content", result != null ? result : "");
        } catch (Exception e) {
            return ToolResult.failure(toolUseId, "browser_get_content", "Bridge error: " + e.getMessage());
        }
    }

    private ToolResult executeBrowserClick(String toolUseId, String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            if (!args.has("selector")) {
                return ToolResult.failure(toolUseId, "browser_click", "Missing required parameter 'selector'");
            }
            Map<String, String> body = Map.of("selector", args.get("selector").asText());
            restTemplate.postForObject(bridgeUrl + "/bridge/browser/click", body, String.class);
            return ToolResult.success(toolUseId, "browser_click", "{\"clicked\":true}");
        } catch (Exception e) {
            return ToolResult.failure(toolUseId, "browser_click", "Bridge error: " + e.getMessage());
        }
    }

    private ToolResult executeBrowserType(String toolUseId, String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            if (!args.has("selector") || !args.has("text")) {
                return ToolResult.failure(toolUseId, "browser_type",
                    "Missing required parameters 'selector' and/or 'text'");
            }
            Map<String, String> body = Map.of(
                "selector", args.get("selector").asText(),
                "text", args.get("text").asText()
            );
            restTemplate.postForObject(bridgeUrl + "/bridge/browser/type", body, String.class);
            return ToolResult.success(toolUseId, "browser_type", "{\"typed\":true}");
        } catch (Exception e) {
            return ToolResult.failure(toolUseId, "browser_type", "Bridge error: " + e.getMessage());
        }
    }

    private ToolResult executeBrowserScreenshot(String toolUseId) {
        try {
            String result = restTemplate.getForObject(bridgeUrl + "/bridge/browser/screenshot", String.class);
            return ToolResult.success(toolUseId, "browser_screenshot", result != null ? result : "{}");
        } catch (Exception e) {
            return ToolResult.failure(toolUseId, "browser_screenshot", "Bridge error: " + e.getMessage());
        }
    }

    private ToolResult executeBrowserExecute(String toolUseId, String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            if (!args.has("js")) {
                return ToolResult.failure(toolUseId, "browser_execute", "Missing required parameter 'js'");
            }
            Map<String, String> body = Map.of("js", args.get("js").asText());
            restTemplate.postForObject(bridgeUrl + "/bridge/browser/execute", body, String.class);
            return ToolResult.success(toolUseId, "browser_execute", "{\"executed\":true}");
        } catch (Exception e) {
            return ToolResult.failure(toolUseId, "browser_execute", "Bridge error: " + e.getMessage());
        }
    }
}