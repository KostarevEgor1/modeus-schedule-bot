package ru.urfu.modeusbot.auth;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import ru.urfu.modeusbot.modeus.ModeusException;

@Component
public class TokenClaimsReader {

    private final ObjectMapper json;

    public TokenClaimsReader(ObjectMapper json) {
        this.json = json;
    }

    public JsonNode read(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException();
            }
            JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (claims == null || !claims.isObject()) {
                throw new IllegalArgumentException();
            }
            return claims;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ModeusException("Не удалось прочитать метаданные токена Modeus.");
        }
    }

    public Instant expiresAt(JsonNode claims) {
        if (!claims.path("exp").canConvertToLong()) {
            throw new ModeusException("В токене Modeus отсутствует срок действия exp.");
        }
        try {
            return Instant.ofEpochSecond(claims.path("exp").asLong());
        } catch (DateTimeException exception) {
            throw new ModeusException("Некорректный срок действия токена Modeus.");
        }
    }

    public String personId(JsonNode claims) {
        String personId = claims.path("person_id").asText("");
        if (personId.isBlank()) {
            throw new ModeusException("В токене нет person_id. Проверьте выбранную учётную запись.");
        }
        return personId;
    }
}
