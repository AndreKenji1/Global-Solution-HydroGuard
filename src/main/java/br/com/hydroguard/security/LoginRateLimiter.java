package br.com.hydroguard.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, per-instance protection; deploy a distributed edge limit for public production. */
@Component
public class LoginRateLimiter {
    private record Window(long start, int count) {}
    private final Map<String,Window> windows = new LinkedHashMap<>();
    private final Clock clock = Clock.systemUTC();
    public synchronized void check(String address) {
        long now = clock.millis();
        windows.entrySet().removeIf(e -> now - e.getValue().start() >= 60_000);
        Window w = windows.get(address);
        if (w != null && w.count() >= 10)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Muitas tentativas. Aguarde um minuto");
        if (windows.size() >= 10_000 && w == null)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Tente novamente em um minuto");
        windows.put(address, new Window(w == null ? now : w.start(), w == null ? 1 : w.count()+1));
    }
}
