package cc.claw.agent;

import cc.claw.agent.tool.ToolDefinition;
import cc.claw.agent.tool.ToolExecutor;
import cc.claw.agent.tool.ToolResult;
import cc.claw.permission.PermissionInterceptor;
import cc.claw.permission.PermissionRequest;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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

    private final AnthropicStreamingChatModel chatModel;
    private final AsyncTaskExecutor executor;
    private final ToolExecutor toolExecutor;
    private final PermissionInterceptor permissionInterceptor;

    public ClaudeService(AnthropicStreamingChatModel chatModel,
                         AsyncTaskExecutor executor,
                         ToolExecutor toolExecutor,
                         PermissionInterceptor permissionInterceptor) {
        this.chatModel = chatModel;
        this.executor = executor;
        this.toolExecutor = toolExecutor;
        this.permissionInterceptor = permissionInterceptor;
    }

    /**
     * Stream chat with agent loop support.
     */
    public void streamMessage(String userMessage,
                              Consumer<String> onDelta,
                              Consumer<ToolCallInfo> onToolCall,
                              Consumer<ToolResultInfo> onToolResult,
                              Consumer<PermissionRequest> onPermissionRequest,
                              Consumer<Throwable> onError,
                              Runnable onComplete) {
        executor.execute(() -> {
            String msgPreview = userMessage.length() > 100
                ? userMessage.substring(0, 100) + "..."
                : userMessage;
            log.info("[Agent] 开始处理: message={}", msgPreview);
            try {
                permissionInterceptor.setOnPermissionRequest(onPermissionRequest);
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
     * Agent loop: call Anthropic via LangChain4j, execute tool_use, repeat until end_turn or max rounds.
     */
    private void runAgentLoop(String userMessage,
                              Consumer<String> onDelta,
                              Consumer<ToolCallInfo> onToolCall,
                              Consumer<ToolResultInfo> onToolResult) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(SYSTEM_PROMPT));
        messages.add(UserMessage.from(userMessage));

        List<ToolSpecification> toolSpecs = toolExecutor.getToolDefinitions().stream()
            .map(ToolDefinition::toolSpecification)
            .toList();

        log.info("[Agent] 工具数量: {}", toolSpecs.size());

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            log.info("[Agent] --- 第{}轮开始 ---", round + 1);

            CompletableFuture<ChatResponse> resultFuture = new CompletableFuture<>();
            ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(toolSpecs)
                .build();

            var handler = new AgentStreamingResponseHandler(onDelta, onToolCall, resultFuture);
            chatModel.chat(request, handler);

            ChatResponse response;
            try {
                response = resultFuture.get(120, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("[Agent] 第{}轮 API异常: {}", round + 1, e.getMessage(), e);
                throw new RuntimeException("Claude API error: " + e.getMessage(), e);
            }

            AiMessage aiMessage = response.aiMessage();
            log.info("[Agent] 第{}轮: 流收集完成, hasToolExecution={}, textLen={}",
                round + 1, aiMessage.hasToolExecutionRequests(),
                aiMessage.text() != null ? aiMessage.text().length() : 0);

            if (!aiMessage.hasToolExecutionRequests()) {
                log.info("[Agent] 第{}轮: 无工具调用, 对话结束", round + 1);
                return;
            }

            List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
            log.info("[Agent] 第{}轮: 执行{}个工具调用", round + 1, toolRequests.size());

            // Add assistant message (with text and tool requests)
            messages.add(aiMessage);

            // Check permission for each tool, skip denied ones
            List<CompletableFuture<ToolResult>> futures = toolRequests.stream()
                .map(req -> {
                    log.info("[Agent] 执行工具: id={}, name={}", req.id(), req.name());
                    try {
                        boolean allowed = permissionInterceptor
                            .intercept(req.id(), req.name(), req.arguments())
                            .get(30, TimeUnit.SECONDS);
                        if (!allowed) {
                            log.warn("[Agent] 工具权限被拒绝: id={}, name={}", req.id(), req.name());
                            return CompletableFuture.completedFuture(
                                ToolResult.failure(req.id(), req.name(), "Permission denied"));
                        }
                    } catch (Exception e) {
                        log.error("[Agent] 权限检查异常: id={}, name={}, error={}",
                            req.id(), req.name(), e.getMessage());
                        return CompletableFuture.completedFuture(
                            ToolResult.failure(req.id(), req.name(), "Permission check failed: " + e.getMessage()));
                    }
                    return toolExecutor.execute(req.id(), req.name(), req.arguments());
                })
                .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Build tool result messages and fire result callbacks
            for (int i = 0; i < futures.size(); i++) {
                ToolResult result = futures.get(i).join();
                ToolExecutionRequest req = toolRequests.get(i);
                log.info("[Agent] 工具结果: id={}, name={}, success={}, contentLen={}",
                    result.toolUseId(), result.toolName(), result.success(), result.content().length());

                ToolExecutionResultMessage resultMsg = ToolExecutionResultMessage.builder()
                    .id(req.id())
                    .toolName(req.name())
                    .text(result.content())
                    .build();
                messages.add(resultMsg);

                onToolResult.accept(new ToolResultInfo(
                    result.toolUseId(), result.toolName(), result.success(),
                    result.success()
                        ? (result.content().length() > 200
                            ? result.content().substring(0, 200) + "..."
                            : result.content())
                        : result.content()));
            }
        }

        log.warn("[Agent] 达到最大轮次{}, 发送超时提示", MAX_TOOL_ROUNDS);
        onDelta.accept("抱歉，处理超时，请简化请求重试。");
    }

    /**
     * Generate a short title (3-8 words) for a conversation based on user messages.
     */
    public String generateTitle(List<String> userMessages) {
        String joined = String.join("\n", userMessages);
        String prompt = "Based on the following user messages, generate a very short title (3-8 words, in the same language as the messages) that summarizes what this conversation is about. Return ONLY the title, no quotes, no explanation.\n\nUser messages:\n" + joined;

        List<ChatMessage> messages = List.of(
            SystemMessage.from("You are a title generator. You ONLY output the title, nothing else."),
            UserMessage.from(prompt)
        );

        try {
            ChatRequest request = ChatRequest.builder().messages(messages).build();
            CompletableFuture<ChatResponse> future = new CompletableFuture<>();
            chatModel.chat(request, new StreamingChatResponseHandler() {
                @Override public void onPartialResponse(String partialResponse) {}
                @Override public void onCompleteResponse(ChatResponse completeResponse) {
                    future.complete(completeResponse);
                }
                @Override public void onError(Throwable error) {
                    future.completeExceptionally(error);
                }
            });
            ChatResponse response = future.get(10, TimeUnit.SECONDS);
            String raw = response.aiMessage().text().trim();
            // Clean up common model artifacts
            raw = raw.replaceAll("^[\"'「」『』]+|[\"'「」『』]+$", "").trim();
            raw = raw.replaceAll("^(Title|标题|主题)[：:]\\s*", "").trim();
            // Replace newlines with spaces
            raw = raw.replaceAll("\\s+", " ").trim();
            return raw.isEmpty() ? null : raw;
        } catch (Exception e) {
            log.error("Failed to generate title", e);
            return null;
        }
    }

    // -- inner class: streaming handler bridge --

    /**
     * Bridges LangChain4j streaming callbacks to ClaudeService's Consumer-based callback interface.
     */
    private static class AgentStreamingResponseHandler implements StreamingChatResponseHandler {
        private final Consumer<String> onDelta;
        private final Consumer<ToolCallInfo> onToolCall;
        private final CompletableFuture<ChatResponse> resultFuture;
        private final Set<String> notifiedToolIds = new HashSet<>();

        private static final Logger log = LoggerFactory.getLogger(AgentStreamingResponseHandler.class);

        AgentStreamingResponseHandler(Consumer<String> onDelta,
                                      Consumer<ToolCallInfo> onToolCall,
                                      CompletableFuture<ChatResponse> resultFuture) {
            this.onDelta = onDelta;
            this.onToolCall = onToolCall;
            this.resultFuture = resultFuture;
        }

        @Override
        public void onPartialResponse(String partialResponse) {
            onDelta.accept(partialResponse);
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            AiMessage aiMessage = completeResponse.aiMessage();
            if (aiMessage.hasToolExecutionRequests()) {
                for (ToolExecutionRequest req : aiMessage.toolExecutionRequests()) {
                    if (notifiedToolIds.add(req.id())) {
                        log.info("[Agent] 流事件 toolCall: id={}, name={}", req.id(), req.name());
                        onToolCall.accept(new ToolCallInfo(req.id(), req.name()));
                    }
                }
            }
            resultFuture.complete(completeResponse);
        }

        @Override
        public void onError(Throwable error) {
            resultFuture.completeExceptionally(error);
        }
    }
}