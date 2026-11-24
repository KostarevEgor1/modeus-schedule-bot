package ru.urfu.modeusbot.auth;

import java.time.Instant;
import java.util.List;

public record ModeusSession(List<String> tokens, String personId, Instant expiresAt) {

    public ModeusSession {
        tokens = List.copyOf(tokens);
    }

    public boolean isUsable() {
        return expiresAt.isAfter(Instant.now().plusSeconds(60));
    }

    @Override
    public String toString() {
        return "ModeusSession[credentials=REDACTED]";
    }
}
