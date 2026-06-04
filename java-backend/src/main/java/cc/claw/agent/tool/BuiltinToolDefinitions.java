package cc.claw.agent.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.util.List;

/**
 * Static definitions for Phase 1's four builtin tools.
 * Each ToolSpecification is built once at class-load time (immutable, thread-safe).
 */
public final class BuiltinToolDefinitions {

    private BuiltinToolDefinitions() {}

    // -- window_watcher --
    public static final ToolDefinition WINDOW_WATCHER = ToolDefinition.of(
        "window_watcher",
        "获取当前活动窗口信息，包括窗口标题、进程名、窗口类名",
        ToolSpecification.builder()
            .name("window_watcher")
            .description("获取当前活动窗口信息，包括窗口标题、进程名、窗口类名")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("processName", "可选：按进程名过滤窗口")
                .build())
            .build()
    );

    // -- clipboard_read --
    public static final ToolDefinition CLIPBOARD_READ = ToolDefinition.of(
        "clipboard_read",
        "读取系统剪贴板的当前文本内容",
        ToolSpecification.builder()
            .name("clipboard_read")
            .description("读取系统剪贴板的当前文本内容")
            .parameters(JsonObjectSchema.builder().build())
            .build()
    );

    // -- clipboard_write --
    public static final ToolDefinition CLIPBOARD_WRITE = ToolDefinition.of(
        "clipboard_write",
        "向系统剪贴板写入文本内容",
        ToolSpecification.builder()
            .name("clipboard_write")
            .description("向系统剪贴板写入文本内容")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("content", "要写入剪贴板的文本内容")
                .required("content")
                .build())
            .build()
    );

    // -- shortcut_register --
    public static final ToolDefinition SHORTCUT_REGISTER = ToolDefinition.of(
        "shortcut_register",
        "注册全局快捷键",
        ToolSpecification.builder()
            .name("shortcut_register")
            .description("注册全局快捷键")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("key", "快捷键组合，如 Alt+Space")
                .addStringProperty("action", "触发快捷键时执行的动作")
                .required("key", "action")
                .build())
            .build()
    );

    public static List<ToolDefinition> all() {
        return List.of(WINDOW_WATCHER, CLIPBOARD_READ, CLIPBOARD_WRITE, SHORTCUT_REGISTER);
    }
}