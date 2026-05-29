package cc.claw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
@EnableConfigurationProperties(ClawConfig.class)
public class ClawApplication {

    private final Environment environment;
    private final ClawConfig clawConfig;

    public ClawApplication(Environment environment, ClawConfig clawConfig) {
        this.environment = environment;
        this.clawConfig = clawConfig;
    }

    public static void main(String[] args) {
        SpringApplication.run(ClawApplication.class, args);
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onReady() {
        String port = environment.getProperty("local.server.port");
        if (port != null) {
            Path portFile = Paths.get(clawConfig.home(), "port");
            try {
                Files.createDirectories(portFile.getParent());
                Files.writeString(portFile, port);
                System.out.println("[c-claw] Backend started on port " + port);
            } catch (IOException e) {
                System.err.println("[c-claw] Failed to write port file: " + e.getMessage());
            }
        }
    }
}