package cc.claw.agent.tool;

import cc.claw.permission.PermissionLevel;
import dev.langchain4j.agent.tool.ToolSpecification;

/**
 * ToolDefinition -- wraps a LangChain4j ToolSpecification with metadata for the agent loop.
 */
public record ToolDefinition(
    String name,
    String description,
    PermissionLevel permissionLevel,
    ToolSpecification toolSpecification
) {
    public static ToolDefinition of(String name, String description, PermissionLevel permissionLevel,
                                     ToolSpecification spec) {
        return new ToolDefinition(name, description, permissionLevel, spec);
    }

    /** Backward-compatible factory with default L1_READONLY permission. */
    public static ToolDefinition of(String name, String description, ToolSpecification spec) {
        return new ToolDefinition(name, description, PermissionLevel.L1_READONLY, spec);
    }
}