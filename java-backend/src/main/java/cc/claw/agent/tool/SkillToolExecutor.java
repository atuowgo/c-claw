package cc.claw.agent.tool;

import cc.claw.ClawConfig;
import cc.claw.permission.PermissionLevel;
import cc.claw.skill.Skill;
import cc.claw.skill.SkillRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Executes tools defined by Skills loaded from ~/.c-claw/skills/.
 * Each skill can define tools in a tools.yaml, which are dispatched via HTTP to the Electron BridgeServer.
 */
@Component
public class SkillToolExecutor implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(SkillToolExecutor.class);

    private final SkillRegistry skillRegistry;
    private final RestTemplate restTemplate;
    private final String bridgeUrl;
    private final ObjectMapper objectMapper;
    private final Map<String, SkillToolDef> toolMap = new LinkedHashMap<>();

    private record SkillToolDef(String skillName, ToolDefinition definition, String method, String path,
                                List<ParamDef> params) {}
    private record ParamDef(String name, String type, boolean required, String description) {}

    public SkillToolExecutor(SkillRegistry skillRegistry, ClawConfig config) {
        this.skillRegistry = skillRegistry;
        this.bridgeUrl = config.bridgeUrl();
        this.objectMapper = new ObjectMapper();

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);

        loadSkillTools();
    }

    // -- ToolExecutor interface --

    @Override
    public List<ToolDefinition> getToolDefinitions() {
        List<ToolDefinition> defs = new ArrayList<>();
        for (SkillToolDef std : toolMap.values()) {
            defs.add(std.definition());
        }
        return defs;
    }

    @Override
    public boolean canExecute(String toolName) {
        return toolMap.containsKey(toolName);
    }

    @Override
    public CompletableFuture<ToolResult> execute(String toolUseId, String toolName, String arguments) {
        return CompletableFuture.supplyAsync(() -> {
            SkillToolDef std = toolMap.get(toolName);
            if (std == null) {
                return ToolResult.failure(toolUseId, toolName, "Unknown skill tool: " + toolName);
            }
            try {
                return dispatch(std, toolUseId, toolName, arguments);
            } catch (Exception e) {
                log.error("Skill tool execution failed: {} - {}", toolName, e.getMessage());
                return ToolResult.failure(toolUseId, toolName, "Bridge error: " + e.getMessage());
            }
        });
    }

    // -- Internal --

    private void loadSkillTools() {
        Map<String, Skill> skills = skillRegistry.getAll();
        if (skills.isEmpty()) {
            log.info("No skills loaded, skip skill tool loading");
            return;
        }

        int count = 0;
        for (Skill skill : skills.values()) {
            Path toolsYaml = Path.of(skill.directoryPath(), "tools.yaml");
            if (!Files.exists(toolsYaml)) {
                continue;
            }

            List<Map<String, Object>> entries;
            try {
                Yaml yaml = new Yaml();
                String content = Files.readString(toolsYaml);
                Map<String, Object> root = yaml.load(content);
                if (root == null) {
                    log.warn("tools.yaml is empty in skill '{}'", skill.name());
                    continue;
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tools = (List<Map<String, Object>>) root.get("tools");
                if (tools == null || tools.isEmpty()) {
                    log.warn("tools.yaml has no tools defined in skill '{}'", skill.name());
                    continue;
                }
                entries = tools;
            } catch (IOException e) {
                log.warn("Failed to read tools.yaml in skill '{}': {}", skill.name(), e.getMessage());
                continue;
            } catch (Exception e) {
                log.warn("Failed to parse tools.yaml in skill '{}': {}", skill.name(), e.getMessage());
                continue;
            }

            for (Map<String, Object> entry : entries) {
                try {
                    SkillToolDef std = parseToolEntry(skill, entry);
                    if (std != null) {
                        toolMap.put(std.definition().name(), std);
                        count++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse tool entry in skill '{}': {}", skill.name(), e.getMessage());
                }
            }
        }
        log.info("Loaded {} skill tool(s)", count);
    }

    @SuppressWarnings("unchecked")
    private SkillToolDef parseToolEntry(Skill skill, Map<String, Object> entry) {
        String name = (String) entry.get("name");
        String description = (String) entry.get("description");
        String permissionStr = (String) entry.get("permission_level");
        String method = (String) entry.get("method");
        String path = (String) entry.get("path");

        if (name == null || name.isBlank()) {
            log.warn("Skill '{}': tool entry missing 'name', skipping", skill.name());
            return null;
        }
        if (path == null || path.isBlank()) {
            log.warn("Skill '{}': tool '{}' missing 'path', skipping", skill.name(), name);
            return null;
        }

        PermissionLevel permissionLevel = parsePermissionLevel(permissionStr);
        String httpMethod = (method != null && !method.isBlank()) ? method.toUpperCase() : "GET";

        // Parse parameters
        List<ParamDef> params = new ArrayList<>();
        List<Map<String, Object>> rawParams = (List<Map<String, Object>>) entry.get("parameters");
        if (rawParams != null) {
            for (Map<String, Object> p : rawParams) {
                String pName = (String) p.get("name");
                String pType = (String) p.getOrDefault("type", "string");
                boolean pRequired = Boolean.TRUE.equals(p.get("required"));
                String pDesc = (String) p.getOrDefault("description", "");
                params.add(new ParamDef(pName, pType, pRequired, pDesc));
            }
        }

        // Build ToolSpecification
        JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder();
        List<String> requiredNames = new ArrayList<>();
        for (ParamDef pd : params) {
            if ("integer".equals(pd.type())) {
                schemaBuilder.addIntegerProperty(pd.name(), pd.description());
            } else {
                schemaBuilder.addStringProperty(pd.name(), pd.description());
            }
            if (pd.required()) {
                requiredNames.add(pd.name());
            }
        }
        if (!requiredNames.isEmpty()) {
            schemaBuilder.required(requiredNames);
        }

        ToolSpecification spec = ToolSpecification.builder()
            .name(name)
            .description(description != null ? description : "")
            .parameters(schemaBuilder.build())
            .build();

        ToolDefinition def = ToolDefinition.of(name,
            description != null ? description : "",
            permissionLevel, spec);

        return new SkillToolDef(skill.name(), def, httpMethod, path, params);
    }

    private PermissionLevel parsePermissionLevel(String value) {
        if (value == null) return PermissionLevel.L0_NONE;
        return switch (value.toUpperCase()) {
            case "L1_READONLY" -> PermissionLevel.L1_READONLY;
            case "L2_LOW_RISK_WRITE" -> PermissionLevel.L2_LOW_RISK_WRITE;
            case "L3_HIGH_RISK" -> PermissionLevel.L3_HIGH_RISK;
            default -> PermissionLevel.L0_NONE;
        };
    }

    private ToolResult dispatch(SkillToolDef std, String toolUseId, String toolName, String arguments) {
        String url = bridgeUrl + std.path();
        try {
            if ("POST".equals(std.method())) {
                JsonNode args = objectMapper.readTree(arguments);
                Map<String, Object> body = buildBody(std, args);
                // Validate required params
                for (ParamDef pd : std.params()) {
                    if (pd.required() && (!args.has(pd.name()) || args.get(pd.name()).isNull())) {
                        return ToolResult.failure(toolUseId, toolName,
                            "Missing required parameter '" + pd.name() + "'");
                    }
                }
                restTemplate.postForObject(url, body, String.class);
                return ToolResult.success(toolUseId, toolName, "{\"ok\":true}");
            } else {
                JsonNode args = objectMapper.readTree(arguments);
                StringBuilder urlBuilder = new StringBuilder(url);
                boolean first = true;
                for (ParamDef pd : std.params()) {
                    if (args.has(pd.name()) && !args.get(pd.name()).isNull()) {
                        urlBuilder.append(first ? '?' : '&');
                        first = false;
                        String value = args.get(pd.name()).asText();
                        urlBuilder.append(pd.name())
                            .append('=')
                            .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
                    } else if (pd.required()) {
                        return ToolResult.failure(toolUseId, toolName,
                            "Missing required parameter '" + pd.name() + "'");
                    }
                }
                String result = restTemplate.getForObject(urlBuilder.toString(), String.class);
                return ToolResult.success(toolUseId, toolName, result != null ? result : "{}");
            }
        } catch (Exception e) {
            return ToolResult.failure(toolUseId, toolName, "Bridge error: " + e.getMessage());
        }
    }

    private Map<String, Object> buildBody(SkillToolDef std, JsonNode args) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (ParamDef pd : std.params()) {
            if (args.has(pd.name()) && !args.get(pd.name()).isNull()) {
                if ("integer".equals(pd.type())) {
                    body.put(pd.name(), args.get(pd.name()).asInt());
                } else {
                    body.put(pd.name(), args.get(pd.name()).asText());
                }
            }
        }
        return body;
    }
}