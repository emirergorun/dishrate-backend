package com.foodboxd.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Locale;
import java.util.regex.Pattern;

public class ValidEmailValidator implements ConstraintValidator<ValidEmail, String> {

    /**
     * yerel-kısım @ alan-adı . uzantı
     *
     * Kasıtlı olarak RFC'nin izin verdiği her şeyi kabul etmez; gerçek dünyada
     * kullanılan adresleri kapsayacak kadar geniş, yazım hatasını yakalayacak
     * kadar dar tutulmuştur:
     *   • yerel kısım: harf, rakam ve . _ % + - ' ! # $ & * / = ? ^ ` { | } ~
     *   • alan adı: nokta ile ayrılmış en az iki etiket (yani "user" reddedilir)
     *   • uzantı: en az iki harf (".com", ".tr", ".com.tr" hepsi geçer)
     *   • ardışık nokta ve baş/son nokta kabul edilmez
     */
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+"
                    + "(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
                    + "@"
                    + "(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+"
                    + "[A-Za-z]{2,}$"
    );

    /** Adresin tamamı için üst sınır (RFC 5321). */
    private static final int MAX_LENGTH = 254;

    /** Yerel kısım (@ öncesi) için üst sınır (RFC 5321). */
    private static final int MAX_LOCAL_PART = 64;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Boş bırakma kuralı @NotBlank'in işi; burada karışmıyoruz.
        if (value == null || value.isBlank()) {
            return true;
        }
        if (value.length() > MAX_LENGTH) {
            return false;
        }
        final int at = value.indexOf('@');
        if (at < 0 || at > MAX_LOCAL_PART) {
            return false;
        }
        return EMAIL.matcher(value.toLowerCase(Locale.ROOT)).matches();
    }
}
