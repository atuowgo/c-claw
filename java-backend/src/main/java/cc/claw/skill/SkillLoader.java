package cc.claw.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

public class SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);

    public Skill load(Path skillDir) {
        Path yamlFile = skillDir.resolve("skill.yaml");
        if (!Files.exists(yamlFile)) {
            log.warn("skill.yaml not found in {}", skillDir);
            return null;
        }

        Map<String, Object> data;
        try {
            Yaml yaml = new Yaml();
            String content = Files.readString(yamlFile);
            data = yaml.load(content);
        } catch (IOException e) {
            log.warn("Failed to read skill.yaml in {}: {}", skillDir, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse skill.yaml in {}: {}", skillDir, e.getMessage());
            return null;
        }

        if (data == null) {
            log.warn("skill.yaml in {} is empty", skillDir);
            return null;
        }

        String name = (String) data.get("name");
        if (name == null || name.isBlank()) {
            log.warn("skill.yaml in {} missing required field 'name'", skillDir);
            return null;
        }

        String version = (String) data.getOrDefault("version", "0.0.0");
        String description = (String) data.getOrDefault("description", "");

        @SuppressWarnings("unchecked")
        List<String> permissions = data.containsKey("permissions")
            ? (List<String>) data.get("permissions")
            : Collections.emptyList();

        @SuppressWarnings("unchecked")
        Map<String, String> dependencies = data.containsKey("dependencies")
            ? (Map<String, String>) data.get("dependencies")
            : Collections.emptyMap();

        String promptContent = "";
        Path promptFile = skillDir.resolve("prompt.md");
        if (Files.exists(promptFile)) {
            try {
                promptContent = Files.readString(promptFile);
            } catch (IOException e) {
                log.warn("Failed to read prompt.md in {}: {}", skillDir, e.getMessage());
            }
        }

        return new Skill(name, version, description, permissions, dependencies, promptContent, skillDir.toString());
    }
}