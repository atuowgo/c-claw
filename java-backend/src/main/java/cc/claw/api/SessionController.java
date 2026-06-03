package cc.claw.api;

import cc.claw.agent.ClaudeService;
import cc.claw.memory.MemoryStore;
import cc.claw.skill.SkillRegistry;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class SessionController {

    private final MemoryStore memoryStore;
    private final SkillRegistry skillRegistry;
    private final ClaudeService claudeService;

    public SessionController(MemoryStore memoryStore, SkillRegistry skillRegistry, ClaudeService claudeService) {
        this.memoryStore = memoryStore;
        this.skillRegistry = skillRegistry;
        this.claudeService = claudeService;
    }

    @GetMapping("/sessions")
    public List<MemoryStore.SessionInfo> listSessions(
            @RequestParam(defaultValue = "10") int limit) {
        return memoryStore.listSessions(limit);
    }

    @PostMapping("/sessions")
    public MemoryStore.SessionInfo createSession() {
        String id = UUID.randomUUID().toString();
        memoryStore.createSession(id, null);
        return memoryStore.getSession(id);
    }

    @DeleteMapping("/sessions/{id}")
    public Map<String, Object> deleteSession(@PathVariable String id) {
        memoryStore.deleteSession(id);
        return Map.of("success", true);
    }

    @PutMapping("/sessions/{id}")
    public Map<String, Object> renameSession(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String title = body.get("title");
        memoryStore.renameSession(id, title);
        return Map.of("success", true);
    }

    @GetMapping("/sessions/{id}/messages")
    public List<MemoryStore.MessageRecord> getMessages(@PathVariable String id) {
        var messages = memoryStore.getMessages(id, 200);
        Collections.reverse(messages);
        return messages;
    }

    @PostMapping("/sessions/{id}/generate-title")
    public Map<String, Object> generateTitle(@PathVariable String id) {
        List<MemoryStore.MessageRecord> messages = memoryStore.getMessages(id, 10);
        List<String> userMessages = messages.stream()
            .filter(m -> "user".equals(m.role()))
            .map(MemoryStore.MessageRecord::content)
            .limit(3)
            .toList();

        if (userMessages.isEmpty()) {
            return Map.of("success", false, "title", "");
        }

        String title = claudeService.generateTitle(userMessages);
        if (title != null && !title.isBlank()) {
            memoryStore.renameSession(id, title);
            return Map.of("success", true, "title", title);
        }
        return Map.of("success", false, "title", "");
    }

    @GetMapping("/skills")
    public Map<String, Object> listSkills() {
        var skills = skillRegistry.getAll();
        var skillList = skills.values().stream()
            .map(s -> Map.of(
                "name", s.name(),
                "version", s.version(),
                "description", s.description() != null ? s.description() : "",
                "directoryPath", s.directoryPath() != null ? s.directoryPath() : ""
            ))
            .toList();
        return Map.of("skills", skillList);
    }
}