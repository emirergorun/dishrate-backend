package com.foodboxd.api.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Yorumdaki uygunsuz ifadeleri yakalar (Apple Guideline 1.2 "süzme" şartı).
 *
 * <p>Liste {@code resources/moderation/banned-words.txt}; biçimi dosyanın
 * başında. Kasıtlı olarak basit: harf oyunlarını ("s1ktir") yakalamaz. Asıl
 * koruma bildirme + engelleme; bu süzgeç bariz olanı kayda girmeden durdurur.
 */
@Component
public class ProfanityFilter {

    private final Set<String> exact = new HashSet<>();
    private final List<String> prefixes = new ArrayList<>();
    /** Birden çok kelimelik ifadeler; her parça kendi kuralıyla ("kök*" ya da tam). */
    private final List<String[]> phrases = new ArrayList<>();

    public ProfanityFilter() throws IOException {
        try (InputStream in = new ClassPathResource("moderation/banned-words.txt").getInputStream()) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                String entry = line.strip();
                if (entry.isEmpty() || entry.startsWith("#")) continue;
                if (entry.contains(" ")) {
                    String[] parts = entry.split("\\s+");
                    for (int i = 0; i < parts.length; i++) {
                        parts[i] = normalizePart(parts[i]);
                    }
                    phrases.add(parts);
                } else if (entry.endsWith("*")) {
                    prefixes.add(SearchText.normalize(entry.substring(0, entry.length() - 1)));
                } else {
                    exact.add(SearchText.normalize(entry));
                }
            }
        }
    }

    /** Metinde listedeki bir ifade geçiyor mu? Boş metin temizdir. */
    public boolean containsProfanity(String text) {
        if (text == null || text.isBlank()) return false;
        // Harf ve rakam dışındaki her şey ayraç: "harika!!!pezevenk" de yakalanır.
        String[] words = SearchText.normalize(text).split("[^\\p{L}\\p{N}]+");
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isEmpty()) continue;
            if (exact.contains(word)) return true;
            for (String p : prefixes) {
                if (word.startsWith(p)) return true;
            }
            for (String[] phrase : phrases) {
                if (phraseAt(words, i, phrase)) return true;
            }
        }
        return false;
    }

    private static boolean phraseAt(String[] words, int start, String[] phrase) {
        if (start + phrase.length > words.length) return false;
        for (int j = 0; j < phrase.length; j++) {
            if (!partMatches(words[start + j], phrase[j])) return false;
        }
        return true;
    }

    /** Parça "kök*" ise baştan eşleşme, değilse tam eşleşme. */
    private static boolean partMatches(String word, String part) {
        return part.endsWith("*")
                ? word.startsWith(part.substring(0, part.length() - 1))
                : word.equals(part);
    }

    private static String normalizePart(String part) {
        return part.endsWith("*")
                ? SearchText.normalize(part.substring(0, part.length() - 1)) + "*"
                : SearchText.normalize(part);
    }
}
