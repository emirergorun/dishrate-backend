package com.foodboxd.api.utils;

import com.foodboxd.api.entities.User;

/**
 * Maskeli ad: "Emir Ergörün" → "E*** E***", yalnız kullanıcı adı varsa
 * "emir_test" → "e***". Artık yalnızca restoran sahibine giden bildirimde
 * kullanılıyor; yorumlar ve engellenenler "@kullanıcıadı" gösteriyor
 * (karar 25 Eylül).
 */
public final class NameMask {

    private NameMask() {}

    public static String of(User u) {
        String first = u.getFirstName();
        String last = u.getLastName();
        if (first != null && !first.isBlank()) {
            String masked = word(first);
            if (last != null && !last.isBlank()) masked += " " + word(last);
            return masked;
        }
        return word(u.getUsername());
    }

    private static String word(String s) {
        String t = s == null ? "" : s.trim();
        return t.isEmpty() ? "***" : t.charAt(0) + "***";
    }
}
