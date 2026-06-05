package cc.claw.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ClaudeServiceIntegrationTest {

    @Autowired
    private ClaudeService claudeService;

    @Test
    void streamMessageReturnsText() throws Exception {
        var future = new CompletableFuture<String>();
        var textBuilder = new StringBuilder();

        claudeService.streamMessage(
            "hello, say hi in one sentence",
            textBuilder::append,
            tc -> {},
            tr -> {},
            pr -> {},
            future::completeExceptionally,
            () -> future.complete(textBuilder.toString())
        );

        String result = future.get(30, TimeUnit.SECONDS);
        assertThat(result).isNotBlank();
        System.out.println("=== Stream API Response ===");
        System.out.println(result);
    }
}