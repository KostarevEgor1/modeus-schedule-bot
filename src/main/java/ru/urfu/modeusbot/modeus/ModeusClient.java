package ru.urfu.modeusbot.modeus;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.urfu.modeusbot.config.ModeusProperties;

@Component
public class ModeusClient {

    private static final int PAGE_SIZE = 500;

    private final RestClient restClient;
    private final ModeusProperties properties;
    private final ModeusResponseMapper mapper;

    public ModeusClient(RestClient modeusRestClient, ModeusProperties properties,
                        ModeusResponseMapper mapper) {
        this.restClient = modeusRestClient;
        this.properties = properties;
        this.mapper = mapper;
    }

    public List<Lesson> getSchedule(LocalDate date) {
        if (!StringUtils.hasText(properties.token()) || !StringUtils.hasText(properties.personId())) {
            throw new ModeusException("Задайте MODEUS_TOKEN и MODEUS_PERSON_ID в окружении приложения.");
        }
        var start = date.atStartOfDay(properties.zoneId());
        var end = date.plusDays(1).atStartOfDay(properties.zoneId()).minusSeconds(1);
        var request = new ScheduleRequest(PAGE_SIZE, start.toOffsetDateTime().toString(),
                end.toOffsetDateTime().toString(), List.of(properties.personId()));
        JsonNode response;
        try {
            response = restClient.post()
                    .uri(builder -> builder.path("/schedule-calendar-v2/api/calendar/events/search")
                            .queryParam("tz", properties.zoneId().getId()).build())
                    .headers(headers -> headers.setBearerAuth(properties.token()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new ModeusException("Modeus отклонил авторизацию. Обновите токен и проверьте доступ.");
            }
            throw new ModeusException("Modeus вернул HTTP " + status + ". Повторите запрос позже.");
        } catch (ResourceAccessException exception) {
            throw new ModeusException("Не удалось соединиться с Modeus. Проверьте сеть и повторите запрос.");
        } catch (RestClientException exception) {
            throw new ModeusException("Не удалось прочитать ответ Modeus.");
        }
        var lessons = mapper.toLessons(response);
        if (lessons.size() >= PAGE_SIZE) {
            throw new ModeusException("Ответ достиг ограничения в 500 занятий; нужна поддержка пагинации.");
        }
        return lessons;
    }

    private record ScheduleRequest(int size, String timeMin, String timeMax,
                                   List<String> attendeePersonId) {
    }
}
