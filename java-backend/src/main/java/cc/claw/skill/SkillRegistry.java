package cc.claw.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);

    private final Map<String, Skill> skills = new LinkedHashMap<>();
    private final Path skillsDir;

    public SkillRegistry() {
        String home = System.getProperty("user.home");
        this.skillsDir = Path.of(home, ".c-claw", "skills");
    }

    @PostConstruct
    void init() {
        if (!Files.exists(skillsDir)) {
            try {
                Files.createDirectories(skillsDir);
                log.info("Created skills directory: {}", skillsDir);
            } catch (IOException e) {
                log.error("Failed to create skills directory {}: {}", skillsDir, e.getMessage());
                return;
            }
        }

        try (var entries = Files.list(skillsDir)) {
            var dirs = entries.filter(Files::isDirectory).toList();
            if (dirs.isEmpty()) {
                log.info("No skills found in {} (directory is empty)", skillsDir);
                return;
            }

            SkillLoader loader = new SkillLoader();
            for (Path dir : dirs) {
                Skill skill = loader.load(dir);
                if (skill != null) {
                    skills.put(skill.name(), skill);
                    log.info("Loaded skill: {} v{}", skill.name(), skill.version());
                }
            }
            log.info("Loaded {} skill(s)", skills.size());
        } catch (IOException e) {
            log.error("Failed to list skills directory {}: {}", skillsDir, e.getMessage());
        }
    }

    public Map<String, Skill> getAll() {
        return Collections.unmodifiableMap(skills);
    }

    public Skill get(String name) {
        return skills.get(name);
    }

    public String getPromptContext() {
        if (skills.isEmpty()) {
            return "";
        }
        return skills.values().stream()
            .map(skill -> {
                StringBuilder sb = new StringBuilder();
                sb.append("## Skill: ").append(skill.name()).append("\n");
                if (skill.description() != null && !skill.description().isBlank()) {
                    sb.append(skill.description()).append("\n\n");
                }
                if (skill.promptContent() != null && !skill.promptContent().isBlank()) {
                    sb.append(skill.promptContent()).append("\n\n");
                }
                return sb.toString();
            })
            .collect(Collectors.joining("---\n\n"));
    }
}