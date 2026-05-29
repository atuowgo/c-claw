package cc.claw.agent.tool;

/**
 * Internal tool execution result. Passed back to Claude as tool_result content block.
 */
public record ToolResult(
    String toolUseId,
    String toolName,
    String content,
    boolean success
) {
    public static ToolResult success(String toolUseId, String toolName, String content) {
        return new ToolResult(toolUseId, toolName, content, true);
    }

    public static ToolResult failure(String toolUseId, String toolName, String error) {
        return new ToolResult(toolUseId, toolName, error, false);
    }
}