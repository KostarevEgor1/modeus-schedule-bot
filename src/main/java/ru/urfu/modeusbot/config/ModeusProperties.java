package ru.urfu.modeusbot.config;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("modeus")
public record ModeusProperties(
        @NotNull URI baseUrl,
        String token,
        String personId,
        @NotNull ZoneId zoneId,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout) {

    @Override
    public String toString() {
        return "ModeusProperties[baseUrl=" + baseUrl + ", zoneId=" + zoneId + "]";
    }
}
