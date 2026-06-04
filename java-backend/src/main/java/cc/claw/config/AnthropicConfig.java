package cc.claw.config;

import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnthropicConfig {

    @Bean
    public AnthropicStreamingChatModel anthropicStreamingChatModel(AnthropicProperties props) {
        String apiKey = resolveApiKey(props);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "Anthropic API key is not configured. " +
                "Set anthropic.key in application.yml or the ANTHROPIC_API_KEY environment variable."
            );
        }

        return AnthropicStreamingChatModel.builder()
            .apiKey(apiKey)
            .modelName(props.modelName())
            .baseUrl(props.baseUrl())
            .maxTokens(4096)
            .logRequests(true)
            .logResponses(true)
            .build();
    }

    private String resolveApiKey(AnthropicProperties props) {
        if (props.key() != null && !props.key().isBlank()) {
            return props.key();
        }
        return System.getenv("ANTHROPIC_API_KEY");
    }
}