package com.cloudfuze.mft.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force protection for login: after too many failures for a given
 * tenant+email key within a window, further attempts are locked out for a cool-down period —
 * even with the correct password. Deliberately simple and per-instance; a multi-node deployment
 * would back this with Redis. Keys are tenant+email (not IP) so one attacker can't lock out an
 * account they don't target, while still stopping password-guessing against a specific account.
 */
@Component
public class LoginThrottle {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    private record Attempts(int failures, Instant lockedUntil) {
    }

    private final Map<String, Attempts> state = new ConcurrentHashMap<>();

    /** @return true if this key is currently locked out. */
    public boolean isLocked(String key) {
        Attempts a = state.get(key);
        return a != null && a.lockedUntil() != null && a.lockedUntil().isAfter(Instant.now());
    }

    public void recordFailure(String key) {
        state.compute(key, (k, a) -> {
            int failures = (a == null ? 0 : a.failures()) + 1;
            Instant lockedUntil = failures >= MAX_FAILURES ? Instant.now().plus(LOCKOUT) : null;
            return new Attempts(failures, lockedUntil);
        });
    }

    public void recordSuccess(String key) {
        state.remove(key);
    }
}
