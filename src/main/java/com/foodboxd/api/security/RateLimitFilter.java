package com.foodboxd.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Basit istek sınırı (1.8). Okuma uçları misafire açılınca giriş yapmamış
 * istekler IP başına sınırlanır; giriş ve kayıt uçları şifre denemesine karşı
 * daha sıkı. Girişli kullanıcı sınırlanmaz.
 *
 * <p>Sabit pencere, bellekte: tek sunucu için yeterli. Yayında vekil sunucu
 * (Cloudflare/nginx) arkasında gerçek IP {@code X-Forwarded-For}'dan
 * okunmalı — şimdilik {@code getRemoteAddr()} (yol haritası 2.x notu).
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /** Giriş yapmamış istekler: IP başına dakikada. */
    static final int ANONYMOUS_PER_MINUTE = 120;
    /** Giriş ve kayıt denemeleri: IP başına dakikada. */
    static final int AUTH_PER_MINUTE = 10;

    private static final long WINDOW_MS = 60_000;

    private record Window(long startedAt, AtomicInteger count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private volatile long lastCleanup = System.currentTimeMillis();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        boolean authAttempt = "POST".equals(request.getMethod())
                && (path.equals("/auth/login") || path.equals("/auth/register"));

        // Görseller sayılmaz: keşfette kaydıran bir misafir yalnız fotoğraflarla
        // sınırı aşardı; dosyalar zaten önbellekli ve salt okunur.
        boolean file = path.startsWith("/files/");

        if (authAttempt || (!file && !isAuthenticated())) {
            String ip = request.getRemoteAddr();
            String bucket = authAttempt ? "auth:" + ip : "anon:" + ip;
            int limit = authAttempt ? AUTH_PER_MINUTE : ANONYMOUS_PER_MINUTE;
            if (!allow(bucket, limit)) {
                log.warn("Rate limit exceeded. Bucket: {}", bucket);
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"status\":429,\"error\":\"too_many_requests\","
                                + "\"message\":\"Çok fazla istek, biraz sonra tekrar dene.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    private boolean allow(String bucket, int limit) {
        long now = System.currentTimeMillis();
        cleanup(now);
        Window w = windows.compute(bucket, (k, old) ->
                old == null || now - old.startedAt() >= WINDOW_MS
                        ? new Window(now, new AtomicInteger())
                        : old);
        return w.count().incrementAndGet() <= limit;
    }

    /** Süresi geçmiş pencereler dakikada bir silinir; harita büyümesin. */
    private void cleanup(long now) {
        if (now - lastCleanup < WINDOW_MS) return;
        lastCleanup = now;
        windows.entrySet().removeIf(e -> now - e.getValue().startedAt() >= WINDOW_MS);
    }
}
