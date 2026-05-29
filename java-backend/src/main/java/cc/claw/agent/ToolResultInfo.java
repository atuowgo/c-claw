package cc.claw.agent;

/**
 * SSE event DTO: emitted when a tool execution completes.
 */
public record ToolResultInfo(String toolUseId, String toolName, boolean success, String summary) {}