package cc.claw.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Primary
@Component
public class CompositeToolExecutor implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(CompositeToolExecutor.class);
    private final List<ToolExecutor> delegates;

    public CompositeToolExecutor(BuiltinToolExecutor builtin, SkillToolExecutor skill) {
        this.delegates = List.of(builtin, skill);
        log.info("CompositeToolExecutor initialized with {} delegate(s)", delegates.size());
    }

    @Override
    public List<ToolDefinition> getToolDefinitions() {
        List<ToolDefinition> all = new ArrayList<>();
        for (ToolExecutor delegate : delegates) {
            all.addAll(delegate.getToolDefinitions());
        }
        return all;
    }

    @Override
    public boolean canExecute(String toolName) {
        for (ToolExecutor delegate : delegates) {
            if (delegate.canExecute(toolName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CompletableFuture<ToolResult> execute(String toolUseId, String toolName, String arguments) {
        for (ToolExecutor delegate : delegates) {
            if (delegate.canExecute(toolName)) {
                return delegate.execute(toolUseId, toolName, arguments);
            }
        }
        return CompletableFuture.completedFuture(
            ToolResult.failure(toolUseId, toolName, "Unknown tool: " + toolName));
    }
}