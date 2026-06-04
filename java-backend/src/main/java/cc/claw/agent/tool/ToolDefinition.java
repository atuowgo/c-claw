package cc.claw.agent.tool;

import dev.langchain4j.agent.tool.ToolSpecification;

/**
 * ToolDefinition -- wraps a LangChain4j ToolSpecification with metadata for the agent loop.
 */
public record ToolDefinition(
    String name,
    String description,
    ToolSpecification toolSpecification
) {
    public static ToolDefinition of(String name, String description, ToolSpecification spec) {
        return new ToolDefinition(name, description, spec);
    }
}