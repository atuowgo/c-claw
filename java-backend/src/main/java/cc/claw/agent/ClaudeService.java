package cc.claw.agent;

import cc.claw.agent.tool.ToolDefinition;
import cc.claw.agent.tool.ToolExecutor;
import cc.claw.agent.tool.ToolResult;
import cc.claw.config.AnthropicProperties;
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
import java.util.stream.Collectors;

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

    private final AnthropicClient client;
    private final String model;
    private final AsyncTaskExecutor executor;
    private final ToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    public ClaudeService(AnthropicClient client,
                         AsyncTaskExecutor executor,
                         ToolExecutor toolExecutor,
                         AnthropicProperties props) {
        this.client = client;
        this.executor = executor;
        this.toolExecutor = toolExecutor;
        this.objectMapper = new ObjectMapper();
        this.model = props.modelName();
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
            String msgPreview = userMessage.length() > 100
                ? userMessage.substring(0, 100) + "..."
                : userMessage;
            log.info("[Agent] 开始处理: message={}", msgPreview);
            try {
                runAgentLoop(userMessage, onDelta, onToolCall, onToolResult);
                log.info("[Agent] 处理完成, 调用onComplete");
                onComplete.run();
            } catch (Exception e) {
                log.error("[Agent] 处理异常: {}", e.getMessage(), e);
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

        log.info("[Agent] 工具数量: {}", tools.size());

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            log.info("[Agent] --- 第{}轮开始 ---", round + 1);
            var params = buildParams(messages, tools);
            CollectedResponse collected;

            try (StreamResponse<RawMessageStreamEvent> stream =
                     client.messages().createStreaming(params)) {
                log.info("[Agent] 第{}轮: API流已建立, 开始收集响应", round + 1);
                collected = collectStreamResponse(stream, onDelta, onToolCall);
                log.info("[Agent] 第{}轮: 流收集完成, textDeltas={}, toolUses={}",
                    round + 1, collected.textDeltaCount(), collected.toolUses().size());
            } catch (Exception e) {
                log.error("[Agent] 第{}轮 API异常: {}", round + 1, e.getMessage(), e);
                throw new RuntimeException("Claude API error: " + e.getMessage(), e);
            }

            List<PendingToolUse> toolUses = collected.toolUses();
            if (toolUses.isEmpty()) {
                log.info("[Agent] 第{}轮: 无工具调用, 对话结束", round + 1);
                return;
            }

            log.info("[Agent] 第{}轮: 执行{}个工具调用", round + 1, toolUses.size());
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
                .map(tu -> {
                    log.info("[Agent] 执行工具: id={}, name={}", tu.id(), tu.name());
                    return toolExecutor.execute(tu.id(), tu.name(), tu.inputJson());
                })
                .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<ContentBlockParam> toolResultBlocks = new ArrayList<>();
            for (CompletableFuture<ToolResult> future : futures) {
                ToolResult result = future.join();
                log.info("[Agent] 工具结果: id={}, name={}, success={}, contentLen={}",
                    result.toolUseId(), result.toolName(), result.success(), result.content().length());
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

        log.warn("[Agent] 达到最大轮次{}, 发送超时提示", MAX_TOOL_ROUNDS);
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
            log.error("[Agent] 工具输入JSON解析失败: tool={}, input={}", tu.name(), tu.inputJson(), e);
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
        int[] textDeltaCount = {0};

        stream.stream().forEach(event -> {
            event.contentBlockStart().ifPresent(start -> {
                start.contentBlock().toolUse().ifPresent(toolUse -> {
                    log.info("[Agent] 流事件 contentBlockStart: index={}, toolId={}, toolName={}",
                        start.index(), toolUse.id(), toolUse.name());
                    toolUseBuilders.put(start.index(),
                        new PendingToolUse.Builder(toolUse.id(), toolUse.name()));
                    onToolCall.accept(new ToolCallInfo(toolUse.id(), toolUse.name()));
                });
                start.contentBlock().text().ifPresent(text -> {
                    log.info("[Agent] 流事件 contentBlockStart: index={}, type=text", start.index());
                });
            });

            event.contentBlockDelta().ifPresent(deltaEvent -> {
                deltaEvent.delta().text().ifPresent(t -> {
                    textDeltaCount[0]++;
                    if (textDeltaCount[0] <= 3) {
                        log.info("[Agent] 流事件 textDelta #{}: \"{}\"", textDeltaCount[0],
                            t.text().length() > 80 ? t.text().substring(0, 80) + "..." : t.text());
                    }
                    onDelta.accept(t.text());
                });
                deltaEvent.delta().inputJson().ifPresent(jsonDelta ->
                    toolUseBuilders.computeIfPresent(deltaEvent.index(), (k, builder) -> {
                        builder.appendInput(jsonDelta.partialJson());
                        return builder;
                    }));
            });
        });

        log.info("[Agent] 流结束: textDelta总数={}, toolUse总数={}",
            textDeltaCount[0], toolUseBuilders.size());

        List<PendingToolUse> toolUses = toolUseBuilders.values().stream()
            .map(PendingToolUse.Builder::build)
            .toList();

        return new CollectedResponse(toolUses, textDeltaCount[0]);
    }

    /** Build MessageCreateParams with tools. */
    private MessageCreateParams buildParams(List<MessageParam> messages, List<Tool> tools) {
        var builder = MessageCreateParams.builder()
            .model(Model.of(model))
            .maxTokens(4096)
            .system(SYSTEM_PROMPT)
            .messages(messages);

        if (!tools.isEmpty()) {
            builder.tools(tools.stream().map(ToolUnion::ofTool).toList());
        }

        log.debug("[Agent] buildParams: model={}, messages={}, tools={}",
            model, messages.size(), tools.size());
        return builder.build();
    }

    /**
     * Generate a short title (3-8 words) for a conversation based on user messages.
     */
    public String generateTitle(List<String> userMessages) {
        String joined = String.join("\n", userMessages);
        String prompt = "Based on the following user messages, generate a very short title (3-8 words, in the same language as the messages) that summarizes what this conversation is about. Return ONLY the title, no quotes, no explanation.\n\nUser messages:\n" + joined;

        var params = MessageCreateParams.builder()
            .model(Model.of(model))
            .maxTokens(50)
            .system("You are a title generator. You ONLY output the title, nothing else.")
            .messages(List.of(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(MessageParam.Content.ofString(prompt))
                .build()))
            .build();

        try {
            var response = client.messages().create(params);
            String raw = response.content().stream()
                .filter(c -> c.text().isPresent())
                .map(c -> c.text().get().text())
                .collect(Collectors.joining())
                .trim();
            // Clean up common model artifacts
            raw = raw.replaceAll("^[\"'「」『』]+|[\"'「」『』]+$", "").trim();
            raw = raw.replaceAll("^(Title|标题|主题)[：:]\s*", "").trim();
            // Replace newlines with spaces
            raw = raw.replaceAll("\\s+", " ").trim();
            return raw.isEmpty() ? null : raw;
        } catch (Exception e) {
            log.error("Failed to generate title", e);
            return null;
        }
    }

    // -- internal records --

    record CollectedResponse(List<PendingToolUse> toolUses, int textDeltaCount) {}

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