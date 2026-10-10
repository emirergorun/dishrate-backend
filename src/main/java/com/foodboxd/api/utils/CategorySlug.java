package com.foodboxd.api.utils;

import java.util.List;

/**
 * Kategorinin değişmeyen anahtarı (2.10b): "Türk Mutfağı" → "turk-mutfagi".
 *
 * <p>Anahtar kategori oluşturulurken bir kez üretilir, sonra ad değişse de
 * aynı kalır. Keşfet bölümleri, istemcinin süzgeci ve ikonlar bu anahtara
 * bakar; Türkçe ad yalnız gösterim içindir.
 */
public final class CategorySlug {

    private CategorySlug() {}

    /**
     * Başlangıç kategorileri, çip sırasıyla. Tohumlayıcılar ve veri göçü sırayı
     * buradan alır; listede olmayan (sonradan eklenen) kategori sona yazılır.
     */
    public static final List<String> DEFAULT_ORDER = List.of(
            "Burger", "Pizza", "Türk Mutfağı", "Ev Yemeği", "Sushi", "Tatlı", "Kahvaltı",
            "İtalyan", "Vegan", "Meze", "Noodle", "Tavuk", "Sandviç");

    /** {@link #DEFAULT_ORDER}'daki sırası (1'den), yoksa null. */
    public static Integer defaultOrder(String name) {
        int i = DEFAULT_ORDER.indexOf(name);
        return i < 0 ? null : i + 1;
    }

    public static String of(String name) {
        String slug = SearchText.normalize(name)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return slug.isEmpty() ? "kategori" : slug;
    }
}
