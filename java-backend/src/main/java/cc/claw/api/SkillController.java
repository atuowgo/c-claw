package cc.claw.api;

import cc.claw.skill.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private static final Logger log = LoggerFactory.getLogger(SkillController.class);
    private final SkillRegistry skillRegistry;
    private final Path skillsDir;

    public SkillController(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
        this.skillsDir = Path.of(System.getProperty("user.home"), ".c-claw", "skills");
    }

    /**
     * Install a skill from a local directory path.
     * MVP: supports local path only. Future: ClawHub download.
     *
     * Request body: { "path": "/path/to/skill/directory" }
     * The skill directory must contain skill.yaml.
     * Copies the directory to ~/.c-claw/skills/{skillName}.
     */
    @PostMapping("/install")
    public Map<String, Object> install(@RequestBody Map<String, String> body) {
        String sourcePath = body.get("path");
        if (sourcePath == null || sourcePath.isBlank()) {
            return Map.of("success", false, "error", "Missing required parameter 'path'");
        }

        Path source = Path.of(sourcePath);
        if (!Files.exists(source) || !Files.isDirectory(source)) {
            return Map.of("success", false, "error", "Source path does not exist or is not a directory: " + sourcePath);
        }

        Path skillYaml = source.resolve("skill.yaml");
        if (!Files.exists(skillYaml)) {
            return Map.of("success", false, "error", "skill.yaml not found in source directory");
        }

        try {
            // Read skill name from skill.yaml
            String yamlContent = Files.readString(skillYaml);
            // Simple extraction of name field (avoid full YAML parse for MVP)
            String skillName = extractYamlField(yamlContent, "name");
            if (skillName == null || skillName.isBlank()) {
                return Map.of("success", false, "error", "skill.yaml missing 'name' field");
            }

            Path targetDir = skillsDir.resolve(skillName);
            if (Files.exists(targetDir)) {
                // Remove existing first
                deleteRecursively(targetDir);
            }

            // Copy directory recursively
            copyDirectory(source, targetDir);

            log.info("Skill installed: {} from {}", skillName, sourcePath);
            return Map.of("success", true, "name", skillName, "path", targetDir.toString());
        } catch (IOException e) {
            log.error("Failed to install skill from {}: {}", sourcePath, e.getMessage());
            return Map.of("success", false, "error", "Install failed: " + e.getMessage());
        }
    }

    /**
     * Uninstall a skill by name.
     * Deletes the skill directory from ~/.c-claw/skills/{name}.
     */
    @DeleteMapping("/{name}")
    public Map<String, Object> uninstall(@PathVariable String name) {
        Path skillDir = skillsDir.resolve(name);
        if (!Files.exists(skillDir)) {
            return Map.of("success", false, "error", "Skill not found: " + name);
        }

        try {
            deleteRecursively(skillDir);
            log.info("Skill uninstalled: {}", name);
            return Map.of("success", true, "name", name);
        } catch (IOException e) {
            log.error("Failed to uninstall skill {}: {}", name, e.getMessage());
            return Map.of("success", false, "error", "Uninstall failed: " + e.getMessage());
        }
    }

    /**
     * Search available skills.
     * MVP: returns a hardcoded list of example skills.
     * Future: query ClawHub registry.
     */
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam(defaultValue = "") String q) {
        // MVP: hardcoded example skills
        List<Map<String, String>> available = List.of(
            Map.of(
                "name", "example-file-organizer",
                "version", "0.1.0",
                "description", "Organize files in a directory by type and date",
                "author", "c-claw-community"
            ),
            Map.of(
                "name", "example-web-scraper",
                "version", "0.1.0",
                "description", "Scrape web content using browser automation",
                "author", "c-claw-community"
            ),
            Map.of(
                "name", "example-code-reviewer",
                "version", "0.1.0",
                "description", "Automated code review using AI analysis",
                "author", "c-claw-community"
            )
        );

        // Filter by query if provided
        List<Map<String, String>> filtered = available;
        if (q != null && !q.isBlank()) {
            String lowerQ = q.toLowerCase();
            filtered = available.stream()
                .filter(s -> s.get("name").toLowerCase().contains(lowerQ)
                    || s.get("description").toLowerCase().contains(lowerQ))
                .toList();
        }

        return Map.of("skills", filtered, "total", filtered.size());
    }

    // -- helper methods --

    private String extractYamlField(String yaml, String field) {
        for (String line : yaml.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(field + ":")) {
                return trimmed.substring(field.length() + 1).trim().replaceAll("^[\"']|[\"']$", "");
            }
        }
        return null;
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(s -> {
            try {
                Path d = target.resolve(source.relativize(s));
                if (Files.isDirectory(s)) {
                    Files.createDirectories(d);
                } else {
                    Files.copy(s, d);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (Files.isDirectory(dir)) {
            try (var entries = Files.list(dir)) {
                for (Path entry : entries.toList()) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.deleteIfExists(dir);
    }
}