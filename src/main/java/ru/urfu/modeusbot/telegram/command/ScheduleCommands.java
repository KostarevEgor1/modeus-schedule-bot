package ru.urfu.modeusbot.telegram.command;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.urfu.modeusbot.modeus.Lesson;
import ru.urfu.modeusbot.schedule.ScheduleService;

@Configuration(proxyBeanMethods = false)
class ScheduleCommands {

    private final ScheduleService schedule;
    private final ScheduleReplyFormatter formatter;

    ScheduleCommands(ScheduleService schedule, ScheduleReplyFormatter formatter) {
        this.schedule = schedule;
        this.formatter = formatter;
    }

    @Bean
    BotCommand todayCommand() {
        return new ScheduleCommand("/today", () -> format("Расписание на сегодня", schedule.getToday()));
    }

    @Bean
    BotCommand tomorrowCommand() {
        return new ScheduleCommand("/tomorrow", () -> format("Расписание на завтра", schedule.getTomorrow()));
    }

    @Bean
    BotCommand weekCommand() {
        return new ScheduleCommand("/week", () -> format("Расписание на текущую неделю", schedule.getCurrentWeek()));
    }

    @Bean
    BotCommand nextCommand() {
        return new ScheduleCommand("/next", () -> schedule.getNextLesson()
                .map(lesson -> format("Ближайшая пара", List.of(lesson)))
                .orElse("В ближайшие семь дней, включая сегодня, предстоящих пар нет."));
    }

    private String format(String heading, List<Lesson> lessons) {
        if (lessons.isEmpty()) {
            return heading + "\nПар нет.";
        }
        return formatter.format(heading, lessons);
    }

    private record ScheduleCommand(String name, Supplier<String> reply) implements BotCommand {

        @Override
        public String execute() {
            return reply.get();
        }
    }
}
