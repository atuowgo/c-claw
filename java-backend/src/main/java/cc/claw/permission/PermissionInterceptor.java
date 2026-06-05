package cc.claw.permission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Intercepts tool execution and enforces permission policy.
 * L0-L2 tools are auto-approved. L3 tools trigger a user approval workflow via SSE callback.
 * L4 tools are auto-denied.
 */
@Component
public class PermissionInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PermissionInterceptor.class);

    private final Map<String, CompletableFuture<Boolean>> pendingRequests = new ConcurrentHashMap<>();
    private volatile Consumer<PermissionRequest> onPermissionRequest;

    /**
     * Register the callback used to notify the frontend (via SSE) of a pending permission request.
     */
    public void setOnPermissionRequest(Consumer<PermissionRequest> callback) {
        this.onPermissionRequest = callback;
    }

    /**
     * Intercept a tool execution. Returns a CompletableFuture that completes with true (allowed)
     * or false (denied/timeout).
     *
     * @param toolUseId unique tool-use identifier from Claude
     * @param toolName  tool name
     * @param arguments tool arguments (for building the permission request description)
     */
    public CompletableFuture<Boolean> intercept(String toolUseId, String toolName, String arguments) {
        PermissionLevel level = PermissionMapping.getLevel(toolName);

        log.info("[Permission] tool={}, level={}", toolName, level);

        switch (level) {
            case L0_NONE:
            case L1_READONLY:
            case L2_LOW_RISK_WRITE:
                return CompletableFuture.completedFuture(true);

            case L4_FORBIDDEN:
                log.warn("[Permission] 工具 {} 被禁止 (L4)", toolName);
                return CompletableFuture.completedFuture(false);

            case L3_HIGH_RISK: {
                String description = buildDescription(toolName, arguments);
                PermissionRequest request = new PermissionRequest(toolUseId, toolName, level, description);

                CompletableFuture<Boolean> future = new CompletableFuture<>();
                pendingRequests.put(toolUseId, future);

                // Auto-reject after 30s timeout
                future.orTimeout(30, TimeUnit.SECONDS);

                // Handle timeout gracefully: log and remove
                future.exceptionally(ex -> {
                    log.warn("[Permission] 工具 {} (id={}) 审批超时, 自动拒绝", toolName, toolUseId);
                    pendingRequests.remove(toolUseId);
                    return false;
                });

                // Notify frontend if callback is registered
                var callback = this.onPermissionRequest;
                if (callback != null) {
                    try {
                        callback.accept(request);
                    } catch (Exception e) {
                        log.error("[Permission] 回调通知失败: {}", e.getMessage(), e);
                        pendingRequests.remove(toolUseId);
                        future.complete(false);
                    }
                } else {
                    log.warn("[Permission] onPermissionRequest 回调未设置, 自动拒绝 L3 工具 {}", toolName);
                    pendingRequests.remove(toolUseId);
                    future.complete(false);
                }

                return future;
            }

            default:
                return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Handle a permission response from the frontend.
     */
    public void respond(PermissionResponse response) {
        CompletableFuture<Boolean> future = pendingRequests.remove(response.toolUseId());
        if (future == null) {
            log.warn("[Permission] 收到未知toolUseId的响应: {}", response.toolUseId());
            return;
        }

        if (response.approved()) {
            log.info("[Permission] 用户批准: toolUseId={}, scope={}", response.toolUseId(), response.scope());
            future.complete(true);
        } else {
            log.info("[Permission] 用户拒绝: toolUseId={}", response.toolUseId());
            future.complete(false);
        }
    }

    private String buildDescription(String toolName, String arguments) {
        if (arguments == null || arguments.isEmpty() || "{}".equals(arguments)) {
            return toolName;
        }
        // Truncate arguments for display
        String trimmed = arguments.length() > 200 ? arguments.substring(0, 200) + "..." : arguments;
        return toolName + ": " + trimmed;
    }
}