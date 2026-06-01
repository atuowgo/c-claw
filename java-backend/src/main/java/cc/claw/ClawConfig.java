package cc.claw;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@ConfigurationProperties(prefix = "claw")
public record ClawConfig(
    @DefaultValue("${user.home}/.c-claw") String home,
    @DefaultValue("19800") int bridgePort
) {
    private static final Logger log = LoggerFactory.getLogger(ClawConfig.class);
    private static volatile Integer cachedBridgePort;

    public Path homePath() {
        return Paths.get(home);
    }

    public String bridgeUrl() {
        int port = resolveBridgePort();
        return "http://127.0.0.1:" + port;
    }

    private int resolveBridgePort() {
        if (cachedBridgePort != null) {
            return cachedBridgePort;
        }
        Path portFile = Paths.get(home, "bridge.port");
        if (Files.exists(portFile)) {
            try {
                String content = Files.readString(portFile).trim();
                cachedBridgePort = Integer.parseInt(content);
                return cachedBridgePort;
            } catch (IOException | NumberFormatException e) {
                log.warn("Failed to read bridge.port, falling back to default port {}", bridgePort);
            }
        }
        cachedBridgePort = bridgePort;
        return cachedBridgePort;
    }
}