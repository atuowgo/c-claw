package cc.claw.agent.tool;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Tool executor interface. Phase 1: BuiltinToolExecutor. Phase 2: SkillToolExecutor.
 */
public interface ToolExecutor {

    List<ToolDefinition> getToolDefinitions();

    boolean canExecute(String toolName);

    CompletableFuture<ToolResult> execute(String toolUseId, String toolName, String arguments);
}