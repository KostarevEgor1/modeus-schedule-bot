package ru.urfu.modeusbot.telegram.command;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;
import ru.urfu.modeusbot.config.ModeusProperties;
import ru.urfu.modeusbot.modeus.Lesson;

@Component
class ScheduleReplyFormatter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, dd.MM", Locale.forLanguageTag("ru"));
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ZoneId zone;

    ScheduleReplyFormatter(ModeusProperties properties) {
        this.zone = properties.zoneId();
    }

    String format(String heading, List<Lesson> lessons) {
        var text = new StringBuilder(heading);
        LocalDate previousDay = null;
        for (Lesson lesson : lessons) {
            var start = lesson.start().atZoneSameInstant(zone);
            var end = lesson.end().atZoneSameInstant(zone);
            LocalDate day = start.toLocalDate();
            if (!day.equals(previousDay)) {
                text.append("\n\n").append(DATE.format(day));
                previousDay = day;
            }
            text.append("\n\n").append(TIME.format(start)).append('–').append(TIME.format(end))
                    .append("  ").append(lesson.title());
            appendDetails(text, "Преподаватели", lesson.teachers());
            appendDetails(text, "Место", lesson.locations());
        }
        return text.toString();
    }

    private void appendDetails(StringBuilder text, String label, List<String> values) {
        if (!values.isEmpty()) {
            text.append('\n').append(label).append(": ").append(String.join("; ", values));
        }
    }
}
