package ru.urfu.modeusbot.cli;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.urfu.modeusbot.config.ModeusProperties;
import ru.urfu.modeusbot.modeus.Lesson;
import ru.urfu.modeusbot.schedule.ScheduleService;

@Component
@ConditionalOnProperty(name = "modeus.check", havingValue = "true")
public class ScheduleCheckRunner implements ApplicationRunner {

    private final ScheduleService schedule;
    private final ModeusProperties properties;

    public ScheduleCheckRunner(ScheduleService schedule, ModeusProperties properties) {
        this.schedule = schedule;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String view = option(args, "modeus.view", "day").toLowerCase(Locale.ROOT);
        if (args.containsOption("modeus.date") && !view.equals("day") && !view.equals("week")) {
            throw new IllegalArgumentException("--modeus.date поддерживается только для day и week.");
        }
        LocalDate date = LocalDate.now(properties.zoneId());
        if (args.containsOption("modeus.date")) {
            try {
                date = LocalDate.parse(option(args, "modeus.date", ""));
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException("Дата должна иметь формат YYYY-MM-DD.");
            }
        }
        List<Lesson> lessons = switch (view) {
            case "day" -> schedule.getDay(date);
            case "today" -> schedule.getToday();
            case "tomorrow" -> schedule.getTomorrow();
            case "week" -> args.containsOption("modeus.date")
                    ? schedule.getWeek(date) : schedule.getCurrentWeek();
            case "next" -> schedule.getNextLesson().stream().toList();
            default -> throw new IllegalArgumentException("Допустимые modeus.view: day, today, tomorrow, week, next.");
        };
        System.out.printf("Режим: %s. Количество занятий: %d%n", view, lessons.size());
        if (lessons.isEmpty()) {
            System.out.println(view.equals("next")
                    ? "В ближайшие 7 дней, включая сегодня, нет предстоящих занятий."
                    : "В выбранном периоде занятий нет.");
        }
        for (var lesson : lessons) {
            var start = lesson.start().atZoneSameInstant(properties.zoneId());
            var end = lesson.end().atZoneSameInstant(properties.zoneId()).toLocalTime();
            System.out.printf("%s %s–%s %s%n", start.toLocalDate(), start.toLocalTime(), end, lesson.title());
            if (!lesson.teachers().isEmpty()) {
                System.out.println("Преподаватели: " + String.join("; ", lesson.teachers()));
            }
            if (!lesson.locations().isEmpty()) {
                System.out.println("Место: " + String.join("; ", lesson.locations()));
            }
        }
    }

    private String option(ApplicationArguments args, String name, String defaultValue) {
        if (!args.containsOption(name)) {
            return defaultValue;
        }
        var values = args.getOptionValues(name);
        if (values == null || values.size() != 1 || values.getFirst().isBlank()) {
            throw new IllegalArgumentException("Укажите одно непустое значение --" + name + ".");
        }
        return values.getFirst();
    }
}
