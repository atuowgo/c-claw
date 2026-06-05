package cc.claw.permission;

/**
 * Permission response from frontend, indicating user's decision.
 * scope: "once" for single use, "always" for persistent approval.
 */
public record PermissionResponse(
    String toolUseId,
    boolean approved,
    String scope
) {}