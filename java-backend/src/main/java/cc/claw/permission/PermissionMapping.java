package cc.claw.permission;

import java.util.List;
import java.util.Map;

/**
 * Static mapping of tool names to permission levels.
 * Supports prefix-based wildcard matching for tool families (e.g. browser_*, desktop_*).
 * Unknown tools default to L4_FORBIDDEN.
 */
public final class PermissionMapping {

    private PermissionMapping() {}

    /** Prefix-based entries: each entry is [prefix, level]. Checked in order; first match wins. */
    private static final List<Map.Entry<String, PermissionLevel>> PREFIX_MAPPINGS = List.of(
        Map.entry("browser_", PermissionLevel.L3_HIGH_RISK),
        Map.entry("desktop_get_windows", PermissionLevel.L1_READONLY),
        Map.entry("desktop_", PermissionLevel.L3_HIGH_RISK),
        Map.entry("file_read", PermissionLevel.L1_READONLY),
        Map.entry("file_list", PermissionLevel.L1_READONLY),
        Map.entry("file_search", PermissionLevel.L1_READONLY),
        Map.entry("file_info", PermissionLevel.L1_READONLY),
        Map.entry("file_write", PermissionLevel.L3_HIGH_RISK)
    );

    /** Exact-match entries. */
    private static final Map<String, PermissionLevel> EXACT_MAPPINGS = Map.of(
        "window_watcher", PermissionLevel.L1_READONLY,
        "clipboard_read", PermissionLevel.L1_READONLY,
        "clipboard_write", PermissionLevel.L2_LOW_RISK_WRITE,
        "shortcut_register", PermissionLevel.L3_HIGH_RISK
    );

    /**
     * Resolve permission level for a given tool name.
     * Checks exact match first, then prefix match in order, then defaults to L4.
     */
    public static PermissionLevel getLevel(String toolName) {
        if (toolName == null || toolName.isEmpty()) {
            return PermissionLevel.L4_FORBIDDEN;
        }

        PermissionLevel exact = EXACT_MAPPINGS.get(toolName);
        if (exact != null) {
            return exact;
        }

        for (var entry : PREFIX_MAPPINGS) {
            if (toolName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        return PermissionLevel.L4_FORBIDDEN;
    }
}