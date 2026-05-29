package cc.claw.agent.tool;

import com.anthropic.models.messages.Tool;

/**
 * ToolDefinition -- wraps an Anthropic Tool object with metadata for the agent loop.
 */
public record ToolDefinition(
    String name,
    String description,
    Tool anthropicTool
) {
    public static ToolDefinition of(String name, String description, Tool anthropicTool) {
        return new ToolDefinition(name, description, anthropicTool);
    }
}