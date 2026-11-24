package ru.urfu.modeusbot.modeus;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class ModeusResponseMapper {

    public List<Lesson> toLessons(JsonNode response) {
        if (response == null || !response.path("_embedded").isObject()) {
            throw new ModeusException("Некорректный ответ Modeus: отсутствует объект _embedded.");
        }
        JsonNode embedded = response.path("_embedded");
        JsonNode events = embedded.path("events");
        if (!events.isArray()) {
            throw new ModeusException("Некорректный ответ Modeus: отсутствует массив events.");
        }
        var courses = index(embedded.path("course-unit-realizations"));
        var persons = index(embedded.path("persons"));
        var rooms = index(embedded.path("rooms"));
        Map<String, List<String>> teachers = new HashMap<>();
        Map<String, List<String>> locations = new HashMap<>();

        for (JsonNode attendee : embedded.path("event-attendees")) {
            if ("TEACH".equals(attendee.path("roleId").asText())) {
                JsonNode person = persons.get(linkId(attendee, "person"));
                if (person != null) {
                    add(teachers, linkId(attendee, "event"), person.path("fullName").asText());
                }
            }
        }
        for (JsonNode eventRoom : embedded.path("event-rooms")) {
            JsonNode room = rooms.get(linkId(eventRoom, "room"));
            if (room != null) {
                String name = room.path("name").asText();
                String address = room.path("building").path("address").asText();
                String location = name.isBlank() ? address
                        : address.isBlank() ? name : name + ", " + address;
                add(locations, linkId(eventRoom, "event"), location);
            }
        }
        for (JsonNode location : embedded.path("event-locations")) {
            String eventId = location.path("eventId").asText(linkId(location, "event"));
            add(locations, eventId, location.path("customLocation").asText());
        }

        Map<String, Lesson> lessons = new LinkedHashMap<>();
        try {
            for (JsonNode event : events) {
                String id = event.path("id").asText();
                if (id.isBlank()) {
                    throw new ModeusException("Некорректный ответ Modeus: занятие без идентификатора.");
                }
                JsonNode course = courses.get(linkId(event, "course-unit-realization"));
                String title = course == null ? "" : course.path("name").asText();
                if (title.isBlank()) {
                    title = event.path("name").asText("Занятие");
                }
                lessons.putIfAbsent(id, new Lesson(id, title,
                        OffsetDateTime.parse(event.path("start").asText()),
                        OffsetDateTime.parse(event.path("end").asText()),
                        event.path("typeId").asText(),
                        teachers.getOrDefault(id, List.of()), locations.getOrDefault(id, List.of())));
            }
        } catch (DateTimeParseException exception) {
            throw new ModeusException("Некорректный ответ Modeus: невозможно прочитать время занятия.");
        }
        return lessons.values().stream().sorted(Comparator.comparing(Lesson::start)).toList();
    }

    private Map<String, JsonNode> index(JsonNode items) {
        Map<String, JsonNode> result = new HashMap<>();
        for (JsonNode item : items) {
            result.put(item.path("id").asText(), item);
        }
        return result;
    }

    private String linkId(JsonNode entity, String relation) {
        JsonNode link = entity.path("_links").path(relation);
        if (link.isArray()) {
            link = link.path(0);
        }
        String href = link.path("href").asText();
        if (href.isBlank()) {
            return "";
        }
        try {
            String path = URI.create(href).getPath().replaceAll("/+$", "");
            return path.substring(path.lastIndexOf('/') + 1);
        } catch (IllegalArgumentException exception) {
            throw new ModeusException("Некорректная ссылка на связанную сущность Modeus.");
        }
    }

    private void add(Map<String, List<String>> target, String id, String value) {
        if (!id.isBlank() && !value.isBlank()) {
            var values = target.computeIfAbsent(id, key -> new ArrayList<>());
            if (!values.contains(value)) {
                values.add(value);
            }
        }
    }
}
