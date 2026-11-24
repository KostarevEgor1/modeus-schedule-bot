package ru.urfu.modeusbot.schedule;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import ru.urfu.modeusbot.modeus.Lesson;

@Service
public class ScheduleService {

    private static final int NEXT_LESSON_SEARCH_DAYS = 7;
    private final ScheduleSource source;
    private final Clock clock;

    public ScheduleService(ScheduleSource source, Clock scheduleClock) {
        this.source = source;
        this.clock = scheduleClock;
    }

    public List<Lesson> getToday() {
        return getDay(LocalDate.now(clock));
    }

    public List<Lesson> getTomorrow() {
        return getDay(LocalDate.now(clock).plusDays(1));
    }

    public List<Lesson> getDay(LocalDate date) {
        Objects.requireNonNull(date, "Укажите дату расписания.");
        return load(date, date);
    }

    public List<Lesson> getCurrentWeek() {
        return getWeek(LocalDate.now(clock));
    }

    public List<Lesson> getWeek(LocalDate date) {
        Objects.requireNonNull(date, "Укажите дату недели.");
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return load(monday, monday.plusDays(6));
    }

    public Optional<Lesson> getNextLesson() {
        var now = clock.instant();
        LocalDate today = now.atZone(clock.getZone()).toLocalDate();
        return load(today, today.plusDays(NEXT_LESSON_SEARCH_DAYS - 1)).stream()
                .filter(lesson -> !lesson.start().toInstant().isBefore(now))
                .findFirst();
    }

    private List<Lesson> load(LocalDate firstDay, LocalDate lastDay) {
        var start = firstDay.atStartOfDay(clock.getZone()).toInstant();
        var end = lastDay.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        Map<String, Lesson> unique = new LinkedHashMap<>();
        for (Lesson lesson : source.getSchedule(firstDay, lastDay)) {
            var lessonStart = lesson.start().toInstant();
            if (!lessonStart.isBefore(start) && lessonStart.isBefore(end)) {
                unique.putIfAbsent(lesson.id(), lesson);
            }
        }
        return unique.values().stream()
                .sorted(Comparator.comparing((Lesson lesson) -> lesson.start().toInstant())
                        .thenComparing(lesson -> lesson.end().toInstant())
                        .thenComparing(Lesson::id))
                .toList();
    }
}
