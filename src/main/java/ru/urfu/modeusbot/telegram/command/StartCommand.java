package ru.urfu.modeusbot.telegram.command;

import org.springframework.stereotype.Component;

@Component
public class StartCommand implements BotCommand {

    @Override
    public String name() {
        return "/start";
    }

    @Override
    public String execute(String arguments) {
        return """
                Привет! Я помощник по расписанию Modeus УрФУ.
                Пока доступны знакомство и справка. Просмотр расписания появится на следующем этапе.

                /help — список доступных команд.
                """;
    }
}
