package ru.urfu.modeusbot.modeus;

import java.time.OffsetDateTime;
import java.util.List;

public record Lesson(
        String id,
        String title,
        OffsetDateTime start,
        OffsetDateTime end,
        String type,
        List<String> teachers,
        List<String> locations) {

    public Lesson {
        teachers = List.copyOf(teachers);
        locations = List.copyOf(locations);
    }
}
