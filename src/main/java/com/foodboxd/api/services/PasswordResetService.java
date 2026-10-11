package com.foodboxd.api.services;

import com.foodboxd.api.entities.PasswordResetCode;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.repositories.PasswordResetCodeRepository;
import com.foodboxd.api.repositories.RefreshTokenRepository;
import com.foodboxd.api.repositories.UserRepository;
import com.foodboxd.api.services.mail.MailDeliveryException;
import com.foodboxd.api.services.mail.MailService;
import com.foodboxd.api.services.mail.MailTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Şifremi unuttum (2.7): e-postaya 6 haneli kod, uygulamanın içinde girilir.
 *
 * <ul>
 *   <li>Kod {@value #CODE_MINUTES} dakika geçerli, en fazla {@value #MAX_ATTEMPTS}
 *       hatalı deneme; yeni kod istenince eskiler geçersiz.</li>
 *   <li>Hesap başına saatte en fazla {@value #MAX_CODES_PER_HOUR} kod (fazlası
 *       sessizce gönderilmez: başkasına mail yağdırılamaz, hesap da belli olmaz).</li>
 *   <li>Kod istenince cevap hep aynı; hesabın var olup olmadığı söylenmez.</li>
 *   <li>Şifre değişince bütün oturumlar kapanır, hatalı giriş kilidi kalkar.</li>
 * </ul>
 * Kodun kendisi saklanmaz, karması saklanır; log'a da yazılmaz (yalnız mail
 * anahtarı yokken {@code LogMailService} metni DEBUG'da gösterir).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final int CODE_MINUTES = 15;
    static final int MAX_ATTEMPTS = 5;
    static final int MAX_CODES_PER_HOUR = 3;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository codeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final LoginAttemptService loginAttemptService;

    @Transactional
    public void requestCode(String identifier) {
        Optional<User> found = findUser(identifier);
        if (found.isEmpty()) {
            log.info("Şifre sıfırlama: hesap bulunamadı (cevap aynı).");
            return;
        }
        User user = found.get();
        Instant now = Instant.now();
        codeRepository.deleteOld(user, now.minus(Duration.ofHours(1)));
        if (codeRepository.countByUserAndCreatedAtAfter(user, now.minus(Duration.ofHours(1)))
                >= MAX_CODES_PER_HOUR) {
            log.warn("Şifre sıfırlama: saatlik kod sınırı doldu. Kullanıcı {}", user.getUserId());
            return;
        }
        codeRepository.invalidateAll(user, now);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        codeRepository.save(PasswordResetCode.builder()
                .user(user)
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(now.plus(Duration.ofMinutes(CODE_MINUTES)))
                .attempts(0)
                .createdAt(now)
                .build());

        MailTemplates.Mail mail = MailTemplates.withCode(
                "Şifre sıfırlama talebin",
                "Şifreni sıfırla",
                List.of("Merhaba " + user.getFirstName() + ",",
                        "Şifreni sıfırlamak için bu kodu uygulamaya gir:"),
                code,
                List.of("Kod " + CODE_MINUTES + " dakika geçerlidir.",
                        "Bu talebi sen yapmadıysan endişelenme, şifren değişmedi. Hesabınla ilgili bir "
                                + "şüphen varsa bu maili yanıtlayarak bize bildirebilirsin."));
        try {
            mailService.send(user.getEmail(), mail.subject(), mail.text(), mail.html());
        } catch (MailDeliveryException e) {
            // Kod gönderilemediyse kullanıcı boşuna beklemesin.
            throw new IllegalStateException("Kod gönderilemedi, biraz sonra tekrar dene.");
        }
        log.info("Şifre sıfırlama kodu gönderildi. Kullanıcı {}", user.getUserId());
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void resetPassword(String identifier, String code, String newPassword) {
        User user = findUser(identifier)
                .orElseThrow(() -> new IllegalArgumentException("Kod hatalı."));
        PasswordResetCode reset = codeRepository.findFirstByUserAndUsedAtIsNullOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new IllegalArgumentException("Kod hatalı."));
        Instant now = Instant.now();

        if (now.isAfter(reset.getExpiresAt())) {
            reset.setUsedAt(now);
            throw new IllegalArgumentException("Kodun süresi doldu, yeni kod iste.");
        }
        if (!passwordEncoder.matches(code, reset.getCodeHash())) {
            reset.setAttempts(reset.getAttempts() + 1);
            if (reset.getAttempts() >= MAX_ATTEMPTS) {
                reset.setUsedAt(now);
                throw new IllegalArgumentException("Kod geçersiz oldu, yeni kod iste.");
            }
            throw new IllegalArgumentException("Kod hatalı.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Yeni şifre eskisiyle aynı olamaz.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        reset.setUsedAt(now);
        refreshTokenRepository.deleteByUser(user);
        loginAttemptService.recordSuccess(user.getEmail());
        loginAttemptService.recordSuccess(user.getUsername());
        log.info("Şifre sıfırlandı, oturumlar kapatıldı. Kullanıcı {}", user.getUserId());
    }

    private Optional<User> findUser(String identifier) {
        String id = UserService.normalize(identifier);
        if (id == null || id.isBlank()) return Optional.empty();
        return userRepository.findByEmail(id).or(() -> userRepository.findByUsername(id));
    }
}
