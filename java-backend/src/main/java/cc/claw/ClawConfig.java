package cc.claw;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.nio.file.Paths;

@ConfigurationProperties(prefix = "claw")
public record ClawConfig(
    @DefaultValue("${user.home}/.c-claw") String home,
    @DefaultValue("19800") int bridgePort
) {
    public Path homePath() {
        return Paths.get(home);
    }

    public String bridgeUrl() {
        return "http://127.0.0.1:" + bridgePort;
    }
}