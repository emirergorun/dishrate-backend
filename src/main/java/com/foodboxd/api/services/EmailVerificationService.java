package com.foodboxd.api.services;

import com.foodboxd.api.entities.EmailVerificationCode;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.exceptions.TooManyRequestsException;
import com.foodboxd.api.repositories.EmailVerificationCodeRepository;
import com.foodboxd.api.repositories.UserRepository;
import com.foodboxd.api.services.mail.MailDeliveryException;
import com.foodboxd.api.services.mail.MailService;
import com.foodboxd.api.services.mail.MailTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * E-posta doğrulama (2.8): kayıtta 6 haneli kod gider, uygulamada girilir.
 * **Yumuşak** doğrulama (karar 11 Ekim): doğrulamamanın bugün bir kısıtı yok,
 * Profil'de hatırlatma kalır.
 *
 * <ul>
 *   <li>Kod {@value #CODE_HOURS} saat geçerli, en fazla {@value #MAX_ATTEMPTS}
 *       hatalı deneme; yeni kod istenince eskiler geçersiz.</li>
 *   <li>Kullanıcı başına saatte en fazla {@value #MAX_CODES_PER_HOUR} kod; fazlası
 *       429 (kişi kendi hesabı için istiyor, 2.7'deki gibi gizlemeye gerek yok).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    static final int CODE_HOURS = 24;
    static final int MAX_ATTEMPTS = 5;
    static final int MAX_CODES_PER_HOUR = 3;

    private final UserRepository userRepository;
    private final EmailVerificationCodeRepository codeRepository;
    private final MailService mailService;
    private final OneTimeCodes oneTimeCodes;

    /**
     * Kayıttan hemen sonra: kod gönderilemezse kayıt bozulmaz, yalnız log'a
     * düşer; kullanıcı Profil'den yeniden ister.
     */
    @Transactional
    public void sendOnRegistration(User user) {
        try {
            send(user);
        } catch (RuntimeException e) {
            log.warn("Kayıt sonrası doğrulama kodu gönderilemedi. Kullanıcı {}: {}",
                    user.getUserId(), e.getMessage());
        }
    }

    /** Profil'den "Kodu tekrar gönder". */
    @Transactional
    public void resend(Long userId) {
        User user = load(userId);
        if (user.getEmailVerifiedAt() != null) {
            throw new IllegalStateException("E-postan zaten doğrulanmış.");
        }
        Instant now = Instant.now();
        codeRepository.deleteOld(user, now.minus(Duration.ofHours(1)), now);
        if (codeRepository.countByUserAndCreatedAtAfter(user, now.minus(Duration.ofHours(1)))
                >= MAX_CODES_PER_HOUR) {
            throw new TooManyRequestsException("Saatlik kod isteme limitine ulaştın. Daha sonra tekrar dene.");
        }
        try {
            send(user);
        } catch (MailDeliveryException e) {
            throw new IllegalStateException("Kod gönderilemedi, biraz sonra tekrar dene.");
        }
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void verify(Long userId, String code) {
        User user = load(userId);
        if (user.getEmailVerifiedAt() != null) return;
        EmailVerificationCode current = codeRepository
                .findFirstByUserAndUsedAtIsNullOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new IllegalArgumentException("Kod hatalı."));
        Instant now = Instant.now();

        if (now.isAfter(current.getExpiresAt())) {
            current.setUsedAt(now);
            throw new IllegalArgumentException("Kodun süresi doldu, yeni kod iste.");
        }
        if (!oneTimeCodes.matches(code, current.getCodeHash())) {
            current.setAttempts(current.getAttempts() + 1);
            if (current.getAttempts() >= MAX_ATTEMPTS) {
                current.setUsedAt(now);
                throw new IllegalArgumentException("Kod geçersiz oldu, yeni kod iste.");
            }
            throw new IllegalArgumentException("Kod hatalı.");
        }
        current.setUsedAt(now);
        user.setEmailVerifiedAt(now);
        log.info("E-posta doğrulandı. Kullanıcı {}", user.getUserId());
    }

    private void send(User user) {
        Instant now = Instant.now();
        codeRepository.invalidateAll(user, now);
        String code = oneTimeCodes.generate();
        codeRepository.save(EmailVerificationCode.builder()
                .user(user)
                .codeHash(oneTimeCodes.hash(code))
                .expiresAt(now.plus(Duration.ofHours(CODE_HOURS)))
                .attempts(0)
                .createdAt(now)
                .build());
        MailTemplates.Mail mail = MailTemplates.withCode(
                "E-posta adresini doğrula",
                "Hoş geldin!",
                List.of("Merhaba " + user.getFirstName() + ",",
                        "E-posta adresini doğrulamak için bu kodu uygulamaya gir:"),
                code,
                List.of("Kod " + CODE_HOURS + " saat geçerlidir.",
                        "Bu hesabı sen açmadıysan bu maili yanıtlayarak bize bildirebilirsin."));
        mailService.send(user.getEmail(), mail.subject(), mail.text(), mail.html());
        log.info("E-posta doğrulama kodu gönderildi. Kullanıcı {}", user.getUserId());
    }

    private User load(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı."));
    }
}
