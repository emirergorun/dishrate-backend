package com.foodboxd.api.security;

import com.foodboxd.api.entities.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    /** HS512 için en az 512 bit. */
    private static final int MIN_KEY_BYTES = 64;

    /**
     * Anahtar açılışta denetlenir (2.13): eksik ya da kısa anahtarla uygulama
     * hiç açılmaz. Eskiden kısa anahtar sessizce sıfırlarla tamamlanıp kabul
     * ediliyordu; zayıf bir anahtar yayına fark edilmeden çıkabilirdi.
     */
    @PostConstruct
    void validateSecret() {
        if (jwtSecret == null || jwtSecret.getBytes().length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT anahtarı eksik ya da kısa: JWT_SECRET en az " + MIN_KEY_BYTES
                            + " bayt olmalı. Üretmek için: openssl rand -base64 64");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    public String getUsernameFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get("username", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT token malformed: {}", e.getMessage());
        } catch (JwtException e) {
            // İmzası bozuk token (jjwt'nin SignatureException'ı) ve diğer
            // jjwt hataları. Önceden java.lang.SecurityException yakalanıyordu;
            // imza hatası filtreden kaçıp isteği 500'e düşürüyordu.
            log.warn("JWT invalid: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT token empty: {}", e.getMessage());
        }
        return false;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
