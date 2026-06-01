package cc.claw.agent;

import cc.claw.agent.tool.ToolDefinition;
import cc.claw.agent.tool.ToolExecutor;
import cc.claw.agent.tool.ToolResult;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Service
public class ClaudeService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);
    private static final String SYSTEM_PROMPT = """
        You are C-Claw, a helpful desktop AI assistant.
        You help users with their tasks. Be concise and helpful.
        You run locally on the user's computer.
        You have access to tools to interact with the user's desktop,
        including reading window information, clipboard access, and shortcut management.
        Use these tools when the user's request requires desktop interaction.
        """;

    private static final int MAX_TOOL_ROUNDS = 10;
    private static final String MODEL = "claude-sonnet-4-20250514";

    private final AnthropicClient client;
    private final AsyncTaskExecutor executor;
    private final ToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    public ClaudeService(AnthropicClient client,
                         AsyncTaskExecutor executor,
                         ToolExecutor toolExecutor) {
        this.client = client;
        this.executor = executor;
        this.toolExecutor = toolExecutor;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Stream chat with agent loop support.
     */
    public void streamMessage(String userMessage,
                              Consumer<String> onDelta,
                              Consumer<ToolCallInfo> onToolCall,
                              Consumer<ToolResultInfo> onToolResult,
                              Consumer<Throwable> onError,
                              Runnable onComplete) {
        executor.execute(() -> {
            try {
                runAgentLoop(userMessage, onDelta, onToolCall, onToolResult);
                onComplete.run();
            } catch (Exception e) {
                log.error("Agent loop error", e);
                onError.accept(e);
            }
        });
    }

    /**
     * Agent loop: call Anthropic, execute tool_use, repeat until end_turn or max rounds.
     */
    private void runAgentLoop(String userMessage,
                              Consumer<String> onDelta,
                              Consumer<ToolCallInfo> onToolCall,
                              Consumer<ToolResultInfo> onToolResult) {
        List<MessageParam> messages = new ArrayList<>();
        messages.add(MessageParam.builder()
            .role(MessageParam.Role.USER)
            .content(MessageParam.Content.ofString(userMessage))
            .build());

        List<Tool> tools = toolExecutor.getToolDefinitions().stream()
            .map(ToolDefinition::anthropicTool)
            .toList();

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            var params = buildParams(messages, tools);
            CollectedResponse collected;

            try (StreamResponse<RawMessageStreamEvent> stream =
                     client.messages().createStreaming(params)) {
                collected = collectStreamResponse(stream, onDelta, onToolCall);
            } catch (Exception e) {
                log.error("Claude API error in round {}", round, e);
                throw new RuntimeException("Claude API error: " + e.getMessage(), e);
            }

            List<PendingToolUse> toolUses = collected.toolUses();
            if (toolUses.isEmpty()) {
                return;
            }

            // Build assistant message with tool_use blocks
            List<ContentBlockParam> assistantBlocks = new ArrayList<>();
            for (PendingToolUse tu : toolUses) {
                ToolUseBlockParam toolUseBlock = buildToolUseParam(tu);
                assistantBlocks.add(ContentBlockParam.ofToolUse(toolUseBlock));
            }
            messages.add(MessageParam.builder()
                .role(MessageParam.Role.ASSISTANT)
                .content(MessageParam.Content.ofBlockParams(assistantBlocks))
                .build());

            // Execute all tool calls in parallel
            List<CompletableFuture<ToolResult>> futures = toolUses.stream()
                .map(tu -> toolExecutor.execute(tu.id(), tu.name(), tu.inputJson()))
                .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<ContentBlockParam> toolResultBlocks = new ArrayList<>();
            for (CompletableFuture<ToolResult> future : futures) {
                ToolResult result = future.join();
                ToolResultBlockParam resultBlock = ToolResultBlockParam.builder()
                    .toolUseId(result.toolUseId())
                    .content(ToolResultBlockParam.Content.ofString(result.content()))
                    .isError(!result.success())
                    .build();
                toolResultBlocks.add(ContentBlockParam.ofToolResult(resultBlock));
                onToolResult.accept(new ToolResultInfo(
                    result.toolUseId(), result.toolName(), result.success(),
                    result.success()
                        ? (result.content().length() > 200
                            ? result.content().substring(0, 200) + "..."
                            : result.content())
                        : result.content()));
            }
            messages.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(MessageParam.Content.ofBlockParams(toolResultBlocks))
                .build());
        }

        onDelta.accept("抱歉，处理超时，请简化请求重试。");
    }

    /** Build ToolUseBlockParam from pending tool use, parsing accumulated JSON input. */
    private ToolUseBlockParam buildToolUseParam(PendingToolUse tu) {
        var inputBuilder = ToolUseBlockParam.Input.builder();
        try {
            JsonNode root = objectMapper.readTree(tu.inputJson());
            var iter = root.fields();
            while (iter.hasNext()) {
                var entry = iter.next();
                inputBuilder.putAdditionalProperty(entry.getKey(), JsonValue.fromJsonNode(entry.getValue()));
            }
        } catch (Exception e) {
            log.error("Failed to parse tool input JSON: input={}", tu.inputJson(), e);
            throw new RuntimeException("Failed to parse tool input JSON for tool " + tu.name(), e);
        }
        return ToolUseBlockParam.builder()
            .id(tu.id())
            .name(tu.name())
            .input(inputBuilder.build())
            .build();
    }

    /** Collect a full streaming response: text deltas + tool_use blocks. */
    private CollectedResponse collectStreamResponse(
            StreamResponse<RawMessageStreamEvent> stream,
            Consumer<String> onDelta,
            Consumer<ToolCallInfo> onToolCall) {

        Map<Long, PendingToolUse.Builder> toolUseBuilders = new LinkedHashMap<>();

        stream.stream().forEach(event -> {
            event.contentBlockStart().ifPresent(start -> {
                start.contentBlock().toolUse().ifPresent(toolUse -> {
                    toolUseBuilders.put(start.index(),
                        new PendingToolUse.Builder(toolUse.id(), toolUse.name()));
                    onToolCall.accept(new ToolCallInfo(toolUse.id(), toolUse.name()));
                });
            });

            event.contentBlockDelta().ifPresent(deltaEvent -> {
                deltaEvent.delta().text().ifPresent(t -> onDelta.accept(t.text()));
                deltaEvent.delta().inputJson().ifPresent(jsonDelta ->
                    toolUseBuilders.computeIfPresent(deltaEvent.index(), (k, builder) -> {
                        builder.appendInput(jsonDelta.partialJson());
                        return builder;
                    }));
            });
        });

        List<PendingToolUse> toolUses = toolUseBuilders.values().stream()
            .map(PendingToolUse.Builder::build)
            .toList();

        return new CollectedResponse(toolUses);
    }

    /** Build MessageCreateParams with tools. */
    private MessageCreateParams buildParams(List<MessageParam> messages, List<Tool> tools) {
        var builder = MessageCreateParams.builder()
            .model(Model.of(MODEL))
            .maxTokens(4096)
            .system(SYSTEM_PROMPT)
            .messages(messages);

        if (!tools.isEmpty()) {
            builder.tools(tools.stream().map(ToolUnion::ofTool).toList());
        }

        return builder.build();
    }

    // -- internal records --

    record CollectedResponse(List<PendingToolUse> toolUses) {}

    record PendingToolUse(String id, String name, String inputJson) {
        static class Builder {
            private final String id;
            private final String name;
            private final StringBuilder inputBuilder = new StringBuilder();

            Builder(String id, String name) {
                this.id = id;
                this.name = name;
            }

            void appendInput(String partialJson) {
                inputBuilder.append(partialJson);
            }

            PendingToolUse build() {
                return new PendingToolUse(id, name, inputBuilder.toString());
            }
        }
    }
}