package cc.claw.memory;

import java.util.List;

public record SessionSummary(
    String sessionId,
    List<MemoryEntry> memories
) {}

record MemoryEntry(
    String type,
    String content,
    String keywords,
    int importance
) {}