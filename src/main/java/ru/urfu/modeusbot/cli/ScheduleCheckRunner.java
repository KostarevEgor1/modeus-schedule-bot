package ru.urfu.modeusbot.cli;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.urfu.modeusbot.config.ModeusProperties;
import ru.urfu.modeusbot.modeus.ModeusClient;

@Component
@ConditionalOnProperty(name = "modeus.check", havingValue = "true")
public class ScheduleCheckRunner implements ApplicationRunner {

    private final ModeusClient client;
    private final ModeusProperties properties;

    public ScheduleCheckRunner(ModeusClient client, ModeusProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDate date = LocalDate.now(properties.zoneId());
        if (args.containsOption("modeus.date")) {
            var values = args.getOptionValues("modeus.date");
            if (values == null || values.size() != 1) {
                throw new IllegalArgumentException("Укажите одну дату: --modeus.date=2026-11-30");
            }
            try {
                date = LocalDate.parse(values.getFirst());
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException("Дата должна иметь формат YYYY-MM-DD.");
            }
        }
        var lessons = client.getSchedule(date);
        System.out.printf("Расписание на %s. Количество занятий: %d%n", date, lessons.size());
        for (var lesson : lessons) {
            var start = lesson.start().atZoneSameInstant(properties.zoneId()).toLocalTime();
            var end = lesson.end().atZoneSameInstant(properties.zoneId()).toLocalTime();
            System.out.printf("%s–%s %s%n", start, end, lesson.title());
            if (!lesson.teachers().isEmpty()) {
                System.out.println("Преподаватели: " + String.join("; ", lesson.teachers()));
            }
            if (!lesson.locations().isEmpty()) {
                System.out.println("Место: " + String.join("; ", lesson.locations()));
            }
        }
    }
}
