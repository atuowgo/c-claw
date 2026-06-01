package cc.claw.api;

import cc.claw.agent.ChatRequest;
import cc.claw.agent.ClaudeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ClaudeService claudeService;

    public ChatController(ClaudeService claudeService) {
        this.claudeService = claudeService;
    }

    @PostMapping("/chat")
    public SseEmitter chat(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120000L);

        claudeService.streamMessage(
            request.message(),
            text -> sendEvent(emitter, "text", Map.of("delta", text)),
            toolCall -> sendEvent(emitter, "tool_call", Map.of(
                "toolUseId", toolCall.toolUseId(),
                "toolName", toolCall.toolName()
            )),
            toolResult -> sendEvent(emitter, "tool_result", Map.of(
                "toolUseId", toolResult.toolUseId(),
                "toolName", toolResult.toolName(),
                "success", toolResult.success(),
                "summary", toolResult.summary()
            )),
            error -> {
                log.error("Chat error", error);
                sendEvent(emitter, "error", Map.of("message", error.getMessage()));
                emitter.complete();
            },
            () -> {
                try {
                    sendEvent(emitter, "done", Map.of());
                } catch (Exception e) {
                    log.error("Failed to send done event", e);
                }
                emitter.complete();
            }
        );

        return emitter;
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event()
                .name(name)
                .data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}