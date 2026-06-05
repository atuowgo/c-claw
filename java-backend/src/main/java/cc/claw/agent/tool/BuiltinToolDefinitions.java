package cc.claw.agent.tool;

import cc.claw.permission.PermissionLevel;
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
        PermissionLevel.L1_READONLY,
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
        PermissionLevel.L1_READONLY,
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
        PermissionLevel.L2_LOW_RISK_WRITE,
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
        PermissionLevel.L3_HIGH_RISK,
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

    // -- desktop_screenshot --
    public static final ToolDefinition DESKTOP_SCREENSHOT = ToolDefinition.of(
        "desktop_screenshot",
        "截取当前屏幕截图，返回截图信息",
        PermissionLevel.L1_READONLY,
        ToolSpecification.builder()
            .name("desktop_screenshot")
            .description("截取当前屏幕截图，返回截图信息")
            .parameters(JsonObjectSchema.builder().build())
            .build()
    );

    // -- desktop_get_windows --
    public static final ToolDefinition DESKTOP_GET_WINDOWS = ToolDefinition.of(
        "desktop_get_windows",
        "获取当前所有窗口列表",
        PermissionLevel.L1_READONLY,
        ToolSpecification.builder()
            .name("desktop_get_windows")
            .description("获取当前所有窗口列表")
            .parameters(JsonObjectSchema.builder().build())
            .build()
    );

    // -- desktop_focus_window --
    public static final ToolDefinition DESKTOP_FOCUS_WINDOW = ToolDefinition.of(
        "desktop_focus_window",
        "聚焦指定窗口",
        PermissionLevel.L3_HIGH_RISK,
        ToolSpecification.builder()
            .name("desktop_focus_window")
            .description("聚焦指定窗口")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("idOrName", "窗口ID或名称")
                .required("idOrName")
                .build())
            .build()
    );

    // -- desktop_screen_info --
    public static final ToolDefinition DESKTOP_SCREEN_INFO = ToolDefinition.of(
        "desktop_screen_info",
        "获取屏幕信息，包括分辨率、缩放比例、光标位置",
        PermissionLevel.L1_READONLY,
        ToolSpecification.builder()
            .name("desktop_screen_info")
            .description("获取屏幕信息，包括分辨率、缩放比例、光标位置")
            .parameters(JsonObjectSchema.builder().build())
            .build()
    );

    // -- file_read --
    public static final ToolDefinition FILE_READ = ToolDefinition.of(
        "file_read",
        "读取文件内容",
        PermissionLevel.L1_READONLY,
        ToolSpecification.builder()
            .name("file_read")
            .description("读取文件内容")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("path", "文件路径")
                .addIntegerProperty("maxBytes", "最大读取字节数（可选）")
                .required("path")
                .build())
            .build()
    );

    // -- file_write --
    public static final ToolDefinition FILE_WRITE = ToolDefinition.of(
        "file_write",
        "写入文件内容",
        PermissionLevel.L3_HIGH_RISK,
        ToolSpecification.builder()
            .name("file_write")
            .description("写入文件内容")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("path", "文件路径")
                .addStringProperty("content", "要写入的内容")
                .required("path", "content")
                .build())
            .build()
    );

    // -- file_list --
    public static final ToolDefinition FILE_LIST = ToolDefinition.of(
        "file_list",
        "列出目录内容",
        PermissionLevel.L1_READONLY,
        ToolSpecification.builder()
            .name("file_list")
            .description("列出目录内容")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("path", "目录路径")
                .required("path")
                .build())
            .build()
    );

    // -- file_search --
    public static final ToolDefinition FILE_SEARCH = ToolDefinition.of(
        "file_search",
        "搜索文件",
        PermissionLevel.L1_READONLY,
        ToolSpecification.builder()
            .name("file_search")
            .description("搜索文件")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("path", "搜索路径")
                .addStringProperty("pattern", "搜索模式，支持通配符如 *.java")
                .required("path", "pattern")
                .build())
            .build()
    );

    // -- file_info --
    public static final ToolDefinition FILE_INFO = ToolDefinition.of(
        "file_info",
        "获取文件信息（大小、修改时间等）",
        PermissionLevel.L1_READONLY,
        ToolSpecification.builder()
            .name("file_info")
            .description("获取文件信息（大小、修改时间等）")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("path", "文件路径")
                .required("path")
                .build())
            .build()
    );

    // -- browser_navigate --
    public static final ToolDefinition BROWSER_NAVIGATE = ToolDefinition.of(
        "browser_navigate",
        "导航到指定URL",
        PermissionLevel.L3_HIGH_RISK,
        ToolSpecification.builder()
            .name("browser_navigate")
            .description("导航到指定URL")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("url", "目标URL")
                .required("url")
                .build())
            .build()
    );

    // -- browser_get_content --
    public static final ToolDefinition BROWSER_GET_CONTENT = ToolDefinition.of(
        "browser_get_content",
        "获取当前页面HTML内容",
        PermissionLevel.L1_READONLY,
        ToolSpecification.builder()
            .name("browser_get_content")
            .description("获取当前页面HTML内容")
            .parameters(JsonObjectSchema.builder().build())
            .build()
    );

    // -- browser_click --
    public static final ToolDefinition BROWSER_CLICK = ToolDefinition.of(
        "browser_click",
        "点击页面元素",
        PermissionLevel.L3_HIGH_RISK,
        ToolSpecification.builder()
            .name("browser_click")
            .description("点击页面元素")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("selector", "CSS选择器")
                .required("selector")
                .build())
            .build()
    );

    // -- browser_type --
    public static final ToolDefinition BROWSER_TYPE = ToolDefinition.of(
        "browser_type",
        "在输入框中输入文本",
        PermissionLevel.L3_HIGH_RISK,
        ToolSpecification.builder()
            .name("browser_type")
            .description("在输入框中输入文本")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("selector", "CSS选择器")
                .addStringProperty("text", "要输入的文本")
                .required("selector", "text")
                .build())
            .build()
    );

    // -- browser_screenshot --
    public static final ToolDefinition BROWSER_SCREENSHOT = ToolDefinition.of(
        "browser_screenshot",
        "截取浏览器页面截图",
        PermissionLevel.L1_READONLY,
        ToolSpecification.builder()
            .name("browser_screenshot")
            .description("截取浏览器页面截图")
            .parameters(JsonObjectSchema.builder().build())
            .build()
    );

    // -- browser_execute --
    public static final ToolDefinition BROWSER_EXECUTE = ToolDefinition.of(
        "browser_execute",
        "在页面中执行JavaScript代码",
        PermissionLevel.L3_HIGH_RISK,
        ToolSpecification.builder()
            .name("browser_execute")
            .description("在页面中执行JavaScript代码")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("js", "要执行的JavaScript代码")
                .required("js")
                .build())
            .build()
    );

    public static List<ToolDefinition> all() {
        return List.of(
            WINDOW_WATCHER, CLIPBOARD_READ, CLIPBOARD_WRITE, SHORTCUT_REGISTER,
            DESKTOP_SCREENSHOT, DESKTOP_GET_WINDOWS, DESKTOP_FOCUS_WINDOW, DESKTOP_SCREEN_INFO,
            FILE_READ, FILE_WRITE, FILE_LIST, FILE_SEARCH, FILE_INFO,
            BROWSER_NAVIGATE, BROWSER_GET_CONTENT, BROWSER_CLICK, BROWSER_TYPE,
            BROWSER_SCREENSHOT, BROWSER_EXECUTE
        );
    }
}