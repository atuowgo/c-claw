package cc.claw.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnthropicConfig {

    @Bean
    public AnthropicClient anthropicClient(AnthropicProperties props) {
        String apiKey = resolveApiKey(props);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "Anthropic API key is not configured. " +
                "Set anthropic.key in application.yml or the ANTHROPIC_API_KEY environment variable."
            );
        }

        return AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .baseUrl(props.baseUrl())
            .build();
    }

    private String resolveApiKey(AnthropicProperties props) {
        if (props.key() != null && !props.key().isBlank()) {
            return props.key();
        }
        return System.getenv("ANTHROPIC_API_KEY");
    }
}