package com.foodboxd.api.services;

import com.foodboxd.api.exceptions.TooManyRequestsException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hesap başına hatalı giriş kilidi (2.9). IP sınırı ({@code RateLimitFilter})
 * IP değiştirerek şifre deneyen birini durdurmaz; bu sınır hesaba bakar.
 *
 * <p>{@value #WINDOW_MINUTES} dakikada {@value #MAX_FAILURES} hatalı denemeden
 * sonra hesap o pencere bitene kadar kilitli: doğru şifre de kabul edilmez.
 * Başarılı giriş sayacı sıfırlar. Var olmayan hesap da aynı sayılır; kilit
 * mesajı hesabın varlığını ele vermez. Bellekte, tek sunucu için.
 *
 * <p>429 döner, 401 değil: mobil 401'de token yenilemeye giriyor.
 */
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES = 10;
    static final int WINDOW_MINUTES = 15;
    private static final long WINDOW_MS = WINDOW_MINUTES * 60_000L;

    private record Attempts(long startedAt, int failures) {}

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    /** Kilitliyse 429 fırlatır. */
    public void checkNotLocked(String identifier) {
        Attempts a = attempts.get(identifier);
        if (a == null) return;
        if (System.currentTimeMillis() - a.startedAt() >= WINDOW_MS) {
            attempts.remove(identifier, a);
            return;
        }
        if (a.failures() >= MAX_FAILURES) {
            throw new TooManyRequestsException(
                    "Çok fazla hatalı deneme. " + WINDOW_MINUTES + " dakika sonra tekrar dene.");
        }
    }

    public void recordFailure(String identifier) {
        long now = System.currentTimeMillis();
        attempts.compute(identifier, (k, old) ->
                old == null || now - old.startedAt() >= WINDOW_MS
                        ? new Attempts(now, 1)
                        : new Attempts(old.startedAt(), old.failures() + 1));
        if (attempts.size() > 10_000) {
            attempts.entrySet().removeIf(e -> now - e.getValue().startedAt() >= WINDOW_MS);
        }
    }

    public void recordSuccess(String identifier) {
        attempts.remove(identifier);
    }
}
