package com.foodboxd.api.utils;

import java.util.Locale;

/**
 * Aramada Türkçe karakter ve büyük/küçük harf farkını yok sayar:
 * "sisli", "ŞİŞLİ" ve "Şişli" aynı şeyi bulur.
 *
 * <p>Aynı dönüşüm hem sorgu metnine (Java) hem kolona (SQL {@code translate})
 * uygulanır; ikisi {@link #SOURCE_CHARS} / {@link #TARGET_CHARS} çiftini paylaştığı için
 * birbirinden sapamaz.
 *
 * <p>{@code translate} önce çalışır, {@code lower} sonra: Postgres'te
 * {@code lower('İ')} yerel ayara göre "i̇" (i + birleşik nokta) verebiliyor.
 */
public final class SearchText {

    private SearchText() {}

    public static final String SOURCE_CHARS = "ÇĞİIÖŞÜÂÎÛçğıöşüâîû";
    public static final String TARGET_CHARS = "cgiiosuaiucgiosuaiu";

    /** "  Beşiktaş  Köfte " → "besiktas kofte" */
    public static String normalize(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.trim().toCharArray()) {
            int i = SOURCE_CHARS.indexOf(c);
            sb.append(i >= 0 ? TARGET_CHARS.charAt(i) : Character.toLowerCase(c));
        }
        return sb.toString()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[%_\\\\]", "")      // LIKE joker karakterleri
                .replaceAll("\\s+", " ");
    }

    /** {@code LIKE} için "%metin%" kalıbı. */
    public static String containsPattern(String text) {
        return "%" + normalize(text) + "%";
    }
}
