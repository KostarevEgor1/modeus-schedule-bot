package ru.urfu.modeusbot.modeus;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.urfu.modeusbot.auth.ModeusSession;
import ru.urfu.modeusbot.auth.ModeusSessionProvider;
import ru.urfu.modeusbot.config.ModeusProperties;

@Component
public class ModeusClient {

    private static final int PAGE_SIZE = 500;

    private final RestClient restClient;
    private final ModeusProperties properties;
    private final ModeusResponseMapper mapper;
    private final ModeusSessionProvider sessions;

    public ModeusClient(RestClient modeusRestClient, ModeusProperties properties,
                        ModeusResponseMapper mapper, ModeusSessionProvider sessions) {
        this.restClient = modeusRestClient;
        this.properties = properties;
        this.mapper = mapper;
        this.sessions = sessions;
    }

    public List<Lesson> getSchedule(LocalDate date) {
        ModeusSession session = sessions.getSession();
        try {
            return getSchedule(date, session);
        } catch (RestClientResponseException exception) {
            // Retry one time only, and only after an authentication rejection in SSO mode.
            if (exception.getStatusCode().value() == 401 && sessions.canLoginAutomatically()) {
                sessions.invalidate(session);
                try {
                    return getSchedule(date, sessions.getSession());
                } catch (RestClientResponseException retryException) {
                    throw apiError(retryException);
                }
            }
            throw apiError(exception);
        }
    }

    private List<Lesson> getSchedule(LocalDate date, ModeusSession session) {
        var start = date.atStartOfDay(properties.zoneId());
        var end = date.plusDays(1).atStartOfDay(properties.zoneId()).minusSeconds(1);
        var request = new ScheduleRequest(PAGE_SIZE, start.toOffsetDateTime().toString(),
                end.toOffsetDateTime().toString(), List.of(session.personId()));
        JsonNode response = null;
        for (int index = 0; index < session.tokens().size(); index++) {
            String token = session.tokens().get(index);
            try {
                response = restClient.post()
                        .uri(builder -> builder.path("/schedule-calendar-v2/api/calendar/events/search")
                                .queryParam("tz", properties.zoneId().getId()).build())
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(JsonNode.class);
                break;
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode().value() != 401 || index == session.tokens().size() - 1) {
                    throw exception;
                }
            } catch (ResourceAccessException exception) {
                throw new ModeusException("Не удалось соединиться с Modeus. Проверьте сеть и повторите запрос.");
            } catch (RestClientException exception) {
                throw new ModeusException("Не удалось прочитать ответ Modeus.");
            }
        }
        var lessons = mapper.toLessons(response);
        if (lessons.size() >= PAGE_SIZE) {
            throw new ModeusException("Ответ достиг ограничения в 500 занятий; нужна поддержка пагинации.");
        }
        return lessons;
    }

    private ModeusException apiError(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new ModeusException("Modeus отклонил авторизацию. Проверьте токен или доступ учётной записи.");
        }
        return new ModeusException("Modeus вернул HTTP " + status + ". Повторите запрос позже.");
    }

    private record ScheduleRequest(int size, String timeMin, String timeMax,
                                   List<String> attendeePersonId) {
    }
}
