package ru.urfu.modeusbot.config;

import java.net.URI;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("modeus.auth")
public record ModeusAuthProperties(
        @NotNull Mode mode,
        String username,
        String password,
        @NotNull URI authUrl,
        @NotNull URI ssoUrl) {

    public enum Mode {
        MANUAL, SSO
    }

    @Override
    public String toString() {
        return "ModeusAuthProperties[mode=" + mode + "]";
    }
}
