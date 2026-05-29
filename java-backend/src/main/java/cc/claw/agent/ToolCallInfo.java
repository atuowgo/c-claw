package cc.claw.agent;

/**
 * SSE event DTO: emitted when Claude initiates a tool call.
 */
public record ToolCallInfo(String toolUseId, String toolName) {}