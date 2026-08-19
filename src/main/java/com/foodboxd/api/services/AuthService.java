package com.foodboxd.api.services;

import com.foodboxd.api.dtos.requests.LoginRequest;
import com.foodboxd.api.dtos.responses.AuthResponse;
import com.foodboxd.api.entities.RefreshToken;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.repositories.RefreshTokenRepository;
import com.foodboxd.api.repositories.UserRepository;
import com.foodboxd.api.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;

    @Value("${jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration-days}")
    private long refreshTokenExpirationDays;

    // -----------------------------------------------------------------------
    // Login: email + şifre → access + refresh token
    // -----------------------------------------------------------------------
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // 'email' alanı artık e-posta VEYA kullanıcı adı olabilir (identifier).
        String identifier = request.getEmail();
        log.info("Login attempt for identifier: {}", identifier);

        User user = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new BadCredentialsException(
                        "Kullanıcı adı/e-posta veya şifre hatalı"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Kullanıcı adı/e-posta veya şifre hatalı");
        }

        // Eski refresh token'ları sil (her cihazda tek aktif token)
        refreshTokenRepository.deleteByUser(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        RefreshToken refreshToken = createRefreshToken(user);

        log.info("Login successful for user ID: {}", user.getUserId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(accessTokenExpirationMs / 1000)
                .user(userService.toResponse(user))
                .build();
    }

    // -----------------------------------------------------------------------
    // Refresh: refresh token → yeni access token
    // -----------------------------------------------------------------------
    @Transactional
    public AuthResponse refresh(String refreshTokenStr) {
        log.debug("Token refresh requested");

        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new ResourceNotFoundException("Geçersiz refresh token"));

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new BadCredentialsException("Refresh token süresi dolmuş, lütfen tekrar giriş yapın");
        }

        User user = refreshToken.getUser();
        String newAccessToken = jwtTokenProvider.generateAccessToken(user);

        // Sliding expiry: her kullanımda 60 gün uzat
        // Sadece 60 gün hiç açmayan kullanıcı tekrar giriş yapar
        refreshToken.setExpiresAt(
                Instant.now().plus(refreshTokenExpirationDays, ChronoUnit.DAYS)
        );
        refreshTokenRepository.save(refreshToken);

        log.debug("Token refreshed for user ID: {}", user.getUserId());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(accessTokenExpirationMs / 1000)
                .user(userService.toResponse(user))
                .build();
    }

    // -----------------------------------------------------------------------
    // Logout: refresh token'ı geçersiz kıl
    // -----------------------------------------------------------------------
    @Transactional
    public void logout(String refreshTokenStr) {
        refreshTokenRepository.findByToken(refreshTokenStr)
                .ifPresent(refreshTokenRepository::delete);
        log.info("User logged out, refresh token invalidated");
    }

    // -----------------------------------------------------------------------
    // Private: refresh token oluştur ve kaydet
    // -----------------------------------------------------------------------
    private RefreshToken createRefreshToken(User user) {
        RefreshToken token = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(refreshTokenExpirationDays, ChronoUnit.DAYS))
                .build();
        return refreshTokenRepository.save(token);
    }
}
