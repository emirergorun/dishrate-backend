package com.foodboxd.api.utils;

import java.util.Locale;

/**
 * Aramada Türkçe karakter ve büyük/küçük harf farkını yok sayar:
 * "sisli", "ŞİŞLİ" ve "Şişli" aynı şeyi bulur.
 *
 * <p>Aynı dönüşüm hem sorgu metnine (Java) hem kolona (SQL {@code translate})
 * uygulanır; ikisi {@link #KAYNAK} / {@link #HEDEF} çiftini paylaştığı için
 * birbirinden sapamaz.
 *
 * <p>{@code translate} önce çalışır, {@code lower} sonra: Postgres'te
 * {@code lower('İ')} yerel ayara göre "i̇" (i + birleşik nokta) verebiliyor.
 */
public final class AramaMetni {

    private AramaMetni() {}

    public static final String KAYNAK = "ÇĞİIÖŞÜÂÎÛçğıöşüâîû";
    public static final String HEDEF = "cgiiosuaiucgiosuaiu";

    /** "  Beşiktaş  Köfte " → "besiktas kofte" */
    public static String normalize(String metin) {
        if (metin == null) return "";
        StringBuilder sb = new StringBuilder(metin.length());
        for (char c : metin.trim().toCharArray()) {
            int i = KAYNAK.indexOf(c);
            sb.append(i >= 0 ? HEDEF.charAt(i) : Character.toLowerCase(c));
        }
        return sb.toString()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[%_\\\\]", "")      // LIKE joker karakterleri
                .replaceAll("\\s+", " ");
    }

    /** {@code LIKE} için "%metin%" kalıbı. */
    public static String icerir(String metin) {
        return "%" + normalize(metin) + "%";
    }
}
