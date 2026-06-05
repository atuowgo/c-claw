package cc.claw.permission;

/**
 * Permission level for tool access control.
 * L0: no access, L1: read-only safe, L2: low-risk write, L3: high-risk requiring user approval, L4: forbidden.
 */
public enum PermissionLevel {
    L0_NONE,
    L1_READONLY,
    L2_LOW_RISK_WRITE,
    L3_HIGH_RISK,
    L4_FORBIDDEN
}