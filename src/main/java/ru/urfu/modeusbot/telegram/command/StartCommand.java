package ru.urfu.modeusbot.telegram.command;

import org.springframework.stereotype.Component;

@Component
public class StartCommand implements BotCommand {

    @Override
    public String name() {
        return "/start";
    }

    @Override
    public String execute() {
        return """
                Привет! Я помощник по расписанию Modeus УрФУ.
                /today — пары на сегодня. Все команды — /help.
                """;
    }
}
