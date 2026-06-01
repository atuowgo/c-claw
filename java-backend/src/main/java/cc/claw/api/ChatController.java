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
        String msgPreview = request.message().length() > 100
            ? request.message().substring(0, 100) + "..."
            : request.message();
        log.info("[Chat] 收到请求: message={}", msgPreview);

        SseEmitter emitter = new SseEmitter(120000L);

        emitter.onTimeout(() -> log.warn("[Chat] SSE超时"));
        emitter.onError(ex -> log.error("[Chat] SSE异常: {}", ex.getMessage()));
        emitter.onCompletion(() -> log.info("[Chat] SSE完成"));

        claudeService.streamMessage(
            request.message(),
            text -> {
                String preview = text.length() > 50 ? text.substring(0, 50) + "..." : text;
                log.debug("[Chat] SSE发送 text: delta={}", preview);
                sendEvent(emitter, "text", Map.of("delta", text));
            },
            toolCall -> {
                log.info("[Chat] SSE发送 tool_call: id={}, name={}", toolCall.toolUseId(), toolCall.toolName());
                sendEvent(emitter, "tool_call", Map.of(
                    "toolUseId", toolCall.toolUseId(),
                    "toolName", toolCall.toolName()
                ));
            },
            toolResult -> {
                log.info("[Chat] SSE发送 tool_result: id={}, name={}, success={}",
                    toolResult.toolUseId(), toolResult.toolName(), toolResult.success());
                sendEvent(emitter, "tool_result", Map.of(
                    "toolUseId", toolResult.toolUseId(),
                    "toolName", toolResult.toolName(),
                    "success", toolResult.success(),
                    "summary", toolResult.summary()
                ));
            },
            error -> {
                log.error("[Chat] SSE发送 error: {}", error.getMessage());
                sendEvent(emitter, "error", Map.of("message", error.getMessage()));
                emitter.complete();
            },
            () -> {
                log.info("[Chat] SSE发送 done");
                sendEvent(emitter, "done", Map.of());
                emitter.complete();
            }
        );

        log.info("[Chat] 返回SseEmitter, timeout=120s");
        return emitter;
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event()
                .name(name)
                .data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.error("[Chat] SSE send失败: event={}, error={}", name, e.getMessage());
            emitter.completeWithError(e);
        }
    }
}