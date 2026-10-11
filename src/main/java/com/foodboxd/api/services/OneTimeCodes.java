package com.foodboxd.api.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Tek kullanımlık 6 haneli kodlar (şifre sıfırlama 2.7, e-posta doğrulama 2.8).
 * Kod veritabanına açık yazılmaz; karması (bcrypt) saklanır.
 */
@Component
@RequiredArgsConstructor
public class OneTimeCodes {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordEncoder passwordEncoder;

    /** "000000"–"999999" */
    public String generate() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    public String hash(String code) {
        return passwordEncoder.encode(code);
    }

    public boolean matches(String code, String hash) {
        return code != null && passwordEncoder.matches(code, hash);
    }
}
