package cc.claw.agent.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;

import java.util.List;
import java.util.Map;

/**
 * Static definitions for Phase 1's four builtin tools.
 * Each Tool is built once at class-load time (immutable, thread-safe).
 */
public final class BuiltinToolDefinitions {

    private BuiltinToolDefinitions() {}

    // -- window_watcher --
    public static final ToolDefinition WINDOW_WATCHER = ToolDefinition.of(
        "window_watcher",
        "获取当前活动窗口信息，包括窗口标题、进程名、窗口类名",
        Tool.builder()
            .name("window_watcher")
            .description("获取当前活动窗口信息，包括窗口标题、进程名、窗口类名")
            .inputSchema(Tool.InputSchema.builder()
                .type(JsonValue.from("object"))
                .properties(Tool.InputSchema.Properties.builder()
                    .putAdditionalProperty("processName",
                        JsonValue.from(Map.of(
                            "type", "string",
                            "description", "可选：按进程名过滤窗口")))
                    .build())
                .build())
            .build()
    );

    // -- clipboard_read --
    public static final ToolDefinition CLIPBOARD_READ = ToolDefinition.of(
        "clipboard_read",
        "读取系统剪贴板的当前文本内容",
        Tool.builder()
            .name("clipboard_read")
            .description("读取系统剪贴板的当前文本内容")
            .inputSchema(Tool.InputSchema.builder()
                .type(JsonValue.from("object"))
                .properties(Tool.InputSchema.Properties.builder().build())
                .build())
            .build()
    );

    // -- clipboard_write --
    public static final ToolDefinition CLIPBOARD_WRITE = ToolDefinition.of(
        "clipboard_write",
        "向系统剪贴板写入文本内容",
        Tool.builder()
            .name("clipboard_write")
            .description("向系统剪贴板写入文本内容")
            .inputSchema(Tool.InputSchema.builder()
                .type(JsonValue.from("object"))
                .properties(Tool.InputSchema.Properties.builder()
                    .putAdditionalProperty("content",
                        JsonValue.from(Map.of(
                            "type", "string",
                            "description", "要写入剪贴板的文本内容")))
                    .build())
                .required(List.of("content"))
                .build())
            .build()
    );

    // -- shortcut_register --
    public static final ToolDefinition SHORTCUT_REGISTER = ToolDefinition.of(
        "shortcut_register",
        "注册全局快捷键",
        Tool.builder()
            .name("shortcut_register")
            .description("注册全局快捷键")
            .inputSchema(Tool.InputSchema.builder()
                .type(JsonValue.from("object"))
                .properties(Tool.InputSchema.Properties.builder()
                    .putAdditionalProperty("key",
                        JsonValue.from(Map.of(
                            "type", "string",
                            "description", "快捷键组合，如 Alt+Space")))
                    .putAdditionalProperty("action",
                        JsonValue.from(Map.of(
                            "type", "string",
                            "description", "触发快捷键时执行的动作")))
                    .build())
                .required(List.of("key", "action"))
                .build())
            .build()
    );

    public static List<ToolDefinition> all() {
        return List.of(WINDOW_WATCHER, CLIPBOARD_READ, CLIPBOARD_WRITE, SHORTCUT_REGISTER);
    }
}