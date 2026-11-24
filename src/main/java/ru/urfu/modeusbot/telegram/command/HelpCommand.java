package ru.urfu.modeusbot.telegram.command;

import org.springframework.stereotype.Component;

@Component
public class HelpCommand implements BotCommand {

    @Override
    public String name() {
        return "/help";
    }

    @Override
    public String execute(String arguments) {
        return """
                Доступные команды:
                /start — знакомство с ботом;
                /help — справка.

                Команды расписания пока не подключены.
                """;
    }
}
