package ru.urfu.modeusbot.auth;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.urfu.modeusbot.config.ModeusAuthProperties;
import ru.urfu.modeusbot.config.ModeusProperties;
import ru.urfu.modeusbot.modeus.ModeusException;

@Service
public class ModeusSessionProvider {

    private final ModeusProperties properties;
    private final ModeusAuthProperties auth;
    private final TokenClaimsReader claimsReader;
    private final ModeusSsoAuthenticator authenticator;
    private ModeusSession cached;

    public ModeusSessionProvider(ModeusProperties properties, ModeusAuthProperties auth,
                                 TokenClaimsReader claimsReader, ModeusSsoAuthenticator authenticator) {
        this.properties = properties;
        this.auth = auth;
        this.claimsReader = claimsReader;
        this.authenticator = authenticator;
    }

    public synchronized ModeusSession getSession() {
        if (cached != null && cached.isUsable()) {
            return cached;
        }
        ModeusSession session = auth.mode() == ModeusAuthProperties.Mode.SSO
                ? authenticator.login() : manualSession();
        if (!session.isUsable()) {
            throw new ModeusException("Токен Modeus истёк или истекает менее чем через минуту.");
        }
        cached = session;
        return session;
    }

    public boolean canLoginAutomatically() {
        return auth.mode() == ModeusAuthProperties.Mode.SSO;
    }

    public synchronized void invalidate(ModeusSession rejected) {
        if (cached == rejected) {
            cached = null;
        }
    }

    private ModeusSession manualSession() {
        if (!StringUtils.hasText(properties.token())) {
            throw new ModeusException("Задайте MODEUS_TOKEN или включите MODEUS_AUTH_MODE=SSO.");
        }
        var claims = claimsReader.read(properties.token());
        String personId = StringUtils.hasText(properties.personId())
                ? properties.personId() : claimsReader.personId(claims);
        return new ModeusSession(List.of(properties.token()), personId, claimsReader.expiresAt(claims));
    }
}
