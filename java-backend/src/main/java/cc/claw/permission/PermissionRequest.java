package cc.claw.permission;

/**
 * Permission request DTO sent to frontend when a tool requires user approval.
 */
public record PermissionRequest(
    String toolUseId,
    String toolName,
    PermissionLevel level,
    String description
) {}