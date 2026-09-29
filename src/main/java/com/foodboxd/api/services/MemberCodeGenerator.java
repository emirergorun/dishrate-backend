package com.foodboxd.api.services;

import com.foodboxd.api.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Set;

/**
 * Adisyondaki üye kodu (1.9): "DR-7K4P2Q" gibi, kullanıcıya özel ve hep aynı.
 *
 * <p>İç kimlik (userId) yerine rastgele kod: sıralı numara toplam kullanıcı
 * sayısını ele verir, ileride açık profilleri 1, 2, 3… diye taramayı
 * kolaylaştırır; sahte veri ve silinen hesaplar yüzünden numaralar zaten
 * anlamsız olurdu (karar 29 Eylül). Alfabede karışan harfler yok: 0/O, 1/I/L, U.
 */
@Component
@RequiredArgsConstructor
public class MemberCodeGenerator {

    static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ";
    static final int LENGTH = 6;

    private final SecureRandom random = new SecureRandom();
    private final UserRepository userRepository;

    /** Veritabanında olmayan yeni bir kod. */
    public String next() {
        return next(Set.of());
    }

    /**
     * @param reserved henüz kaydedilmemiş ama bu işlemde verilmiş kodlar
     *                 (toplu oluşturmada birbirleriyle çakışmasınlar)
     */
    public String next(Set<String> reserved) {
        while (true) {
            StringBuilder code = new StringBuilder(LENGTH);
            for (int i = 0; i < LENGTH; i++) {
                code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String candidate = code.toString();
            if (!reserved.contains(candidate) && !userRepository.existsByMemberCode(candidate)) {
                return candidate;
            }
        }
    }
}
