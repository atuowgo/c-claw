package cc.claw.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.MessageParam;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ClaudeServiceIntegrationTest {

    @Autowired
    private AnthropicClient client;

    @Autowired
    private ClaudeService claudeService;

    @Test
    void directCallReturnsText() {
        var params = MessageCreateParams.builder()
            .model(Model.of("kimi-k2.5-aliyun"))
            .maxTokens(256)
            .messages(List.of(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(MessageParam.Content.ofString("hello, say hi in one sentence"))
                .build()))
            .build();

        var response = client.messages().create(params);
        var text = response.content().get(0).asText().text();

        assertThat(text).isNotBlank();
        System.out.println("=== Direct API Response ===");
        System.out.println(text);
    }

    @Test
    void streamMessageReturnsText() throws Exception {
        var future = new CompletableFuture<String>();
        var textBuilder = new StringBuilder();

        claudeService.streamMessage(
            "hello, say hi in one sentence",
            textBuilder::append,
            tc -> {},
            tr -> {},
            future::completeExceptionally,
            () -> future.complete(textBuilder.toString())
        );

        String result = future.get(30, TimeUnit.SECONDS);
        assertThat(result).isNotBlank();
        System.out.println("=== Stream API Response ===");
        System.out.println(result);
    }
}