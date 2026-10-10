package com.foodboxd.api.security;

import com.foodboxd.api.entities.User;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * İstek sınırı (1.8, 2.9). Sınırlar tek yerde, aşağıdaki sabitlerde:
 *
 * <ul>
 *   <li>Giriş yapmamış istek: IP başına dakikada {@value #ANONYMOUS_PER_MINUTE}.</li>
 *   <li>Giriş ve kayıt denemesi: IP başına dakikada {@value #AUTH_PER_MINUTE};
 *       kayıt ayrıca saatte {@value #REGISTER_PER_HOUR} (toplu sahte hesap).</li>
 *   <li>Girişli kullanıcının yazma isteği (puan, yorum, istek listesi, bildirim…):
 *       kullanıcı başına dakikada {@value #WRITES_PER_MINUTE}; fotoğraf yükleme
 *       dakikada {@value #UPLOADS_PER_MINUTE}. Normal kullanım bunlara yaklaşmaz.</li>
 * </ul>
 *
 * <p>Hesap başına hatalı giriş kilidi ayrı: {@code LoginAttemptService}.
 * Mail gönderen uçlar (2.7/2.8) gelince sınırları buraya eklenecek.
 *
 * <p>Sabit pencere, bellekte: tek sunucu için yeterli. IP
 * {@code getRemoteAddr()}'dan okunur; sunucu vekil (platform/Cloudflare)
 * arkasındayken gerçek IP'yi Tomcat {@code X-Forwarded-For}'dan çıkarır
 * ({@code server.forward-headers-strategy=native}), başlığa yalnız güvenilen
 * vekilden gelirse inanır — istemci taklit edemez.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    static final int ANONYMOUS_PER_MINUTE = 120;
    static final int AUTH_PER_MINUTE = 10;
    static final int REGISTER_PER_HOUR = 20;
    static final int WRITES_PER_MINUTE = 60;
    static final int UPLOADS_PER_MINUTE = 10;

    private static final long MINUTE_MS = 60_000;
    private static final long HOUR_MS = 60 * MINUTE_MS;

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private record Window(long startedAt, long lengthMs, AtomicInteger count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private volatile long lastCleanup = System.currentTimeMillis();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        String method = request.getMethod();
        String ip = request.getRemoteAddr();
        boolean login = "POST".equals(method) && path.equals("/auth/login");
        boolean register = "POST".equals(method) && path.equals("/auth/register");
        User user = currentUser();

        boolean allowed;
        if (login || register) {
            allowed = allow("auth:" + ip, AUTH_PER_MINUTE, MINUTE_MS)
                    && (!register || allow("register:" + ip, REGISTER_PER_HOUR, HOUR_MS));
        } else if (user == null) {
            // Görseller sayılmaz: keşfette kaydıran bir misafir yalnız
            // fotoğraflarla sınırı aşardı; dosyalar önbellekli ve salt okunur.
            allowed = path.startsWith("/files/") || allow("anon:" + ip, ANONYMOUS_PER_MINUTE, MINUTE_MS);
        } else if (WRITE_METHODS.contains(method)) {
            String id = "user:" + user.getUserId();
            boolean upload = "POST".equals(method) && path.equals("/files");
            allowed = allow("write:" + id, WRITES_PER_MINUTE, MINUTE_MS)
                    && (!upload || allow("upload:" + id, UPLOADS_PER_MINUTE, MINUTE_MS));
        } else {
            allowed = true;
        }

        if (!allowed) {
            log.warn("Rate limit exceeded. {} {} ip={} user={}", method, path, ip,
                    user == null ? "-" : user.getUserId());
            TooManyRequests.write(response, "Çok fazla istek, biraz sonra tekrar dene.");
            return;
        }
        chain.doFilter(request, response);
    }

    private static User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth.getPrincipal() instanceof User u ? u : null;
    }

    private boolean allow(String bucket, int limit, long windowMs) {
        long now = System.currentTimeMillis();
        cleanup(now);
        Window w = windows.compute(bucket, (k, old) ->
                old == null || now - old.startedAt() >= old.lengthMs()
                        ? new Window(now, windowMs, new AtomicInteger())
                        : old);
        return w.count().incrementAndGet() <= limit;
    }

    /** Süresi geçmiş pencereler dakikada bir silinir; harita büyümesin. */
    private void cleanup(long now) {
        if (now - lastCleanup < MINUTE_MS) return;
        lastCleanup = now;
        windows.entrySet().removeIf(e -> now - e.getValue().startedAt() >= e.getValue().lengthMs());
    }

    /** 429 cevabı; filtre ve servis aynı biçimi kullanır. */
    static final class TooManyRequests {
        private TooManyRequests() {}

        static void write(HttpServletResponse response, String message) throws IOException {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":429,\"error\":\"too_many_requests\",\"message\":\""
                    + message.replace("\"", "\\\"") + "\"}");
        }
    }
}
