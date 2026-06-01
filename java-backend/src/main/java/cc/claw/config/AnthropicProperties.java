package cc.claw.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "anthropic")
public record AnthropicProperties(
    @DefaultValue("https://api.anthropic.com") String baseUrl,
    @DefaultValue("claude-sonnet-4-20250514") String modelName,
    String key
) {}