package ru.urfu.modeusbot.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("telegram.bot")
public record TelegramBotProperties(boolean enabled, String token, String username, @Positive Long ownerId) {

    @AssertTrue(message = "При включённом Telegram задайте TELEGRAM_BOT_TOKEN и TELEGRAM_BOT_USERNAME без @.")
    public boolean isConfigurationValid() {
        return !enabled || (StringUtils.hasText(token) && StringUtils.hasText(username)
                && username.matches("[A-Za-z0-9_]{5,32}") && !token.chars().anyMatch(Character::isWhitespace));
    }

    @Override
    public String toString() {
        return "TelegramBotProperties[enabled=" + enabled + "]";
    }
}
