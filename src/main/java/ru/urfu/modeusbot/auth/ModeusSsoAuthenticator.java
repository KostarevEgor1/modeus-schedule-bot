package ru.urfu.modeusbot.auth;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.net.ssl.SSLException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import ru.urfu.modeusbot.config.ModeusAuthProperties;
import ru.urfu.modeusbot.config.ModeusProperties;
import ru.urfu.modeusbot.modeus.ModeusException;

@Component
public class ModeusSsoAuthenticator {

    private static final int MAX_STEPS = 25;
    private final ModeusProperties properties;
    private final ModeusAuthProperties auth;
    private final TokenClaimsReader claimsReader;
    private final ObjectMapper json;

    public ModeusSsoAuthenticator(ModeusProperties properties, ModeusAuthProperties auth,
                                  TokenClaimsReader claimsReader, ObjectMapper json) {
        this.properties = properties;
        this.auth = auth;
        this.claimsReader = claimsReader;
        this.json = json;
    }

    public ModeusSession login() {
        if (!StringUtils.hasText(auth.username()) || !StringUtils.hasText(auth.password())) {
            throw new ModeusException("Для SSO задайте MODEUS_USERNAME и MODEUS_PASSWORD.");
        }
        try (var client = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()) {
            return login(client);
        }
    }

    ModeusSession login(HttpClient client) {
        try {
            URI configUri = properties.baseUrl().resolve("/schedule-calendar/assets/app.config.json");
            var configResponse = send(client, configUri, null);
            var config = json.readTree(configResponse.body());
            String clientId = config == null ? "" : config.path("wso").path("clientId").asText("");
            if (clientId.isBlank()) {
                throw new ModeusException("Modeus не вернул clientId в конфигурации приложения.");
            }
            String state = UUID.randomUUID().toString();
            String nonce = UUID.randomUUID().toString();
            URI callback = properties.baseUrl().resolve("/schedule-calendar/my");
            URI uri = URI.create(auth.authUrl().resolve("/oauth2/authorize") + "?" + encode(Map.of(
                    "response_type", "id_token token", "client_id", clientId,
                    "redirect_uri", callback.toString(), "scope", "openid",
                    "state", state, "nonce", nonce)));
            Map<String, String> fields = null;
            boolean passwordSubmitted = false;

            for (int step = 0; step < MAX_STEPS; step++) {
                var response = send(client, uri, fields);
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    String location = response.headers().firstValue("Location").orElse("");
                    if (location.isBlank()) {
                        throw new ModeusException("В перенаправлении SSO отсутствует Location.");
                    }
                    URI next = uri.resolve(location);
                    requireTrusted(next);
                    if (next.getRawFragment() != null) {
                        return readSession(next, callback, state, nonce);
                    }
                    if (fields != null && (status == 307 || status == 308)) {
                        throw new ModeusException("SSO запросил неподдерживаемый повтор POST-запроса.");
                    }
                    uri = next;
                    fields = null;
                    continue;
                }

                var document = Jsoup.parse(response.body());
                Element samlInput = document.selectFirst("input[name=SAMLResponse]");
                if (samlInput != null) {
                    Element form = samlInput.closest("form");
                    if (form == null) {
                        throw new ModeusException("В SAML-ответе отсутствует форма отправки.");
                    }
                    URI target = formTarget(uri, form);
                    if (!sameOrigin(target, auth.authUrl())) {
                        throw new ModeusException("SAML-форма ведёт на неожиданный сервер.");
                    }
                    fields = formFields(form);
                    uri = target;
                    continue;
                }

                Element passwordInput = document.selectFirst("input[name=Password]");
                if (passwordInput != null) {
                    if (passwordSubmitted) {
                        throw new ModeusException("SSO не принял логин/пароль или требует дополнительный вход.");
                    }
                    Element form = passwordInput.closest("form");
                    if (form == null) {
                        throw new ModeusException("На странице SSO отсутствует форма входа.");
                    }
                    URI target = formTarget(uri, form);
                    if (!sameOrigin(uri, auth.ssoUrl()) || !sameOrigin(target, auth.ssoUrl())) {
                        throw new ModeusException("Форма пароля находится вне доверенного сервера SSO УрФУ.");
                    }
                    fields = formFields(form);
                    fields.put("UserName", auth.username());
                    fields.put("Password", auth.password());
                    if (!StringUtils.hasText(fields.get("AuthMethod"))) {
                        fields.put("AuthMethod", "FormsAuthentication");
                    }
                    passwordSubmitted = true;
                    uri = target;
                    continue;
                }
                throw new ModeusException("Не удалось продолжить вход SSO: возможны MFA, CAPTCHA или изменение формы входа.");
            }
            throw new ModeusException("Превышено число шагов авторизации Modeus.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModeusException("Вход в Modeus прерван.");
        } catch (JsonProcessingException exception) {
            throw new ModeusException("Modeus вернул конфигурацию приложения в неожиданном формате вместо JSON.");
        } catch (IllegalArgumentException exception) {
            throw new ModeusException("Modeus вернул некорректные данные авторизации.");
        }
    }

    private HttpResponse<String> send(HttpClient client, URI uri, Map<String, String> fields)
            throws InterruptedException {
        requireTrusted(uri);
        var builder = HttpRequest.newBuilder(uri).timeout(properties.readTimeout())
                .header("User-Agent", "Mozilla/5.0 ModeusScheduleBot/0.0.1");
        if (fields == null) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(encode(fields)));
        }
        HttpResponse<String> response;
        try {
            response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw networkError(uri, fields, exception);
        }
        if (response.statusCode() >= 400) {
            throw new ModeusException("Сервер авторизации вернул HTTP " + response.statusCode()
                    + ". Проверьте данные входа и доступность SSO.");
        }
        return response;
    }

    private ModeusException networkError(URI uri, Map<String, String> fields, IOException exception) {
        String stage = uri.getPath().equals("/schedule-calendar/assets/app.config.json")
                ? "загрузка конфигурации Modeus"
                : fields == null ? "получение страницы авторизации"
                : fields.containsKey("Password") ? "отправка формы входа УрФУ"
                : fields.containsKey("SAMLResponse") ? "передача SAML-ответа в Modeus"
                : "отправка формы авторизации";
        String reason = "ошибка ввода-вывода";
        Throwable cause = exception;
        for (int depth = 0; cause != null && depth < 10; depth++, cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException) {
                reason = "превышено время ожидания ответа или соединения";
                break;
            }
            if (cause instanceof SSLException) {
                reason = "не удалось установить защищённое TLS-соединение";
                break;
            }
            if (cause instanceof UnknownHostException
                    || cause instanceof java.nio.channels.UnresolvedAddressException) {
                reason = "не удалось определить IP-адрес сервера";
                break;
            }
            if (cause instanceof ConnectException) {
                reason = "не удалось установить соединение с сервером";
            }
        }
        return new ModeusException("Ошибка входа: " + stage + ". Сервер: " + uri.getHost()
                + ". Причина: " + reason + " [" + exception.getClass().getSimpleName() + "].");
    }

    private ModeusSession readSession(URI uri, URI callback, String state, String nonce) {
        if (!sameOrigin(uri, callback) || !uri.getPath().equals(callback.getPath())) {
            throw new ModeusException("Токены пришли на неожиданный адрес возврата Modeus.");
        }
        var values = decode(uri.getRawFragment());
        if (!state.equals(values.get("state"))) {
            throw new ModeusException("Проверка state при входе в Modeus не пройдена.");
        }
        String idToken = values.get("id_token");
        if (!StringUtils.hasText(idToken)) {
            throw new ModeusException("Modeus не выдал id_token. Возможно, вход был отклонён.");
        }
        var claims = claimsReader.read(idToken);
        if (!nonce.equals(claims.path("nonce").asText())) {
            throw new ModeusException("Проверка nonce при входе в Modeus не пройдена.");
        }
        var tokens = new ArrayList<String>();
        tokens.add(idToken);
        String accessToken = values.get("access_token");
        if (StringUtils.hasText(accessToken) && !idToken.equals(accessToken)) {
            tokens.add(accessToken);
        }
        return new ModeusSession(tokens, claimsReader.personId(claims), claimsReader.expiresAt(claims));
    }

    private URI formTarget(URI page, Element form) {
        URI target = page.resolve(form.attr("action"));
        requireTrusted(target);
        if (!"post".equalsIgnoreCase(form.attr("method"))) {
            throw new ModeusException("Форма авторизации не использует POST.");
        }
        return target;
    }

    private Map<String, String> formFields(Element form) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (Element input : form.select("input[name]:not([disabled])")) {
            fields.put(input.attr("name"), input.val());
        }
        return fields;
    }

    private void requireTrusted(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                || !(sameOrigin(uri, properties.baseUrl()) || sameOrigin(uri, auth.authUrl())
                || sameOrigin(uri, auth.ssoUrl()))) {
            throw new ModeusException("Авторизация остановлена: переход за пределы доверенных HTTPS-серверов.");
        }
    }

    private boolean sameOrigin(URI first, URI second) {
        return first.getScheme() != null && first.getHost() != null
                && first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private int effectivePort(URI uri) {
        return uri.getPort() == -1 ? 443 : uri.getPort();
    }

    private String encode(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private Map<String, String> decode(String fragment) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String pair : fragment.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            if (fields.putIfAbsent(key, value) != null) {
                throw new ModeusException("Modeus вернул повторяющиеся параметры авторизации.");
            }
        }
        return fields;
    }
}
