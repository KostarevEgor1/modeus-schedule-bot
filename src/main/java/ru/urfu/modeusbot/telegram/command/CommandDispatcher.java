package ru.urfu.modeusbot.telegram.command;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import ru.urfu.modeusbot.config.TelegramBotProperties;

@Component
public class CommandDispatcher {

    private final Map<String, BotCommand> commands;
    private final String botUsername;

    public CommandDispatcher(List<BotCommand> commands, TelegramBotProperties properties) {
        Map<String, BotCommand> registered = new HashMap<>();
        for (BotCommand command : commands) {
            if (registered.putIfAbsent(command.name(), command) != null) {
                throw new IllegalStateException("Зарегистрировано несколько обработчиков одной команды.");
            }
        }
        this.commands = Map.copyOf(registered);
        this.botUsername = properties.username();
    }

    public Optional<String> dispatch(String text) {
        if (text == null || text.isBlank()) {
            return Optional.of(unknownCommand());
        }
        String name = text.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int mention = name.indexOf('@');
        if (name.startsWith("/") && mention >= 0) {
            if (!name.substring(mention + 1).equalsIgnoreCase(botUsername)) {
                return Optional.empty();
            }
            name = name.substring(0, mention);
        }
        BotCommand command = commands.get(name);
        return Optional.of(command == null ? unknownCommand() : command.execute());
    }

    private String unknownCommand() {
        return "Неизвестная команда. Напиши /help, чтобы посмотреть доступные команды.";
    }
}
