package cc.claw.skill;

import java.util.List;
import java.util.Map;

public record Skill(
    String name,
    String version,
    String description,
    List<String> permissions,
    Map<String, String> dependencies,
    String promptContent,
    String directoryPath
) {}