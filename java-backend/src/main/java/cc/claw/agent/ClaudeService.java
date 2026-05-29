package cc.claw.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.RawMessageStreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;
import com.anthropic.models.messages.TextDelta;

@Service
public class ClaudeService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);
    private static final String SYSTEM_PROMPT = """
        You are C-Claw, a helpful desktop AI assistant.
        You help users with their tasks. Be concise and helpful.
        You run locally on the user's computer.
        """;

    private final AnthropicClient client;
    private final AsyncTaskExecutor executor;

    public ClaudeService(AnthropicClient client, AsyncTaskExecutor executor) {
        this.client = client;
        this.executor = executor;
    }

    public void streamMessage(String userMessage,
                              Consumer<String> onDelta,
                              Consumer<Throwable> onError,
                              Runnable onComplete) {
        executor.execute(() -> {
            try {
                var params = MessageCreateParams.builder()
                    .model(Model.of("claude-sonnet-4-20250514"))
                    .maxTokens(4096)
                    .system(SYSTEM_PROMPT)
                    .addUserMessage(userMessage)
                    .build();

                try (StreamResponse<RawMessageStreamEvent> stream =
                         client.messages().createStreaming(params)) {
                    stream.stream().forEach(event -> {
                        event.contentBlockDelta().ifPresent(delta -> {
                            delta.delta().text().map(TextDelta::text).ifPresent(onDelta);
                        });
                    });
                }
                onComplete.run();
            } catch (Exception e) {
                log.error("Claude API error", e);
                onError.accept(e);
            }
        });
    }
}