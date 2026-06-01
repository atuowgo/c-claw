package cc.claw.agent.tool;

import cc.claw.ClawConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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
        this.restTemplate = new RestTemplate();
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
                url += "?processName=" + args.get("processName").asText();
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
}