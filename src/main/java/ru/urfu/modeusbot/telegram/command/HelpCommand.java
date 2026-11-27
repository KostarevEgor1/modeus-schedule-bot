package ru.urfu.modeusbot.telegram.command;

import org.springframework.stereotype.Component;

@Component
public class HelpCommand implements BotCommand {

    @Override
    public String name() {
        return "/help";
    }

    @Override
    public String execute() {
        return """
                Доступные команды:
                /today — пары на сегодня
                /tomorrow — пары на завтра
                /week — расписание текущей недели
                /next — ближайшая пара в пределах семи дней, включая сегодня
                /id — свой Telegram ID
                /help — эта справка
                """;
    }
}
