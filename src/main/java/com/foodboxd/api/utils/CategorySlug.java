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
     *
     * <p>22 Ekim'de 13'ten 30'a çıktı (Emir): kategori "canının çektiği yemek
     * türü", her yemeğin tek kategorisi var. "Türk Mutfağı" bölündü (Kebap,
     * Döner, Köfte, Pide & Lahmacun, Mantı…), "Noodle" Asya oldu; Pide ve
     * Lahmacun 23 Ekim'de birleşti (aynı dükkan, aynı fırın; anahtar
     * {@code pide-lahmacun}). İtalyan
     * makarna ve pizza dışını kapsar (makarnalar Makarna'da). Kahve ve İçecek
     * Keşfet'in genel bölümlerine girmez ({@code FeedService}).
     */
    public static final List<String> DEFAULT_ORDER = List.of(
            "Burger", "Pizza", "Kebap", "Döner", "Köfte", "Pide & Lahmacun", "Mantı",
            "Makarna", "Ev Yemeği", "Çorba", "Balık", "Et", "Tavuk", "Sokak Lezzeti",
            "Sandviç", "Atıştırmalık", "Kahvaltı", "Meze", "Salata", "Vegan", "İtalyan",
            "Sushi", "Asya", "Ortadoğu", "Meksika", "Fırın", "Tatlı", "Kahve", "İçecek");

    /** İçecek kategorileri: Keşfet'in genel bölümlerinde görünmez, yalnız çipleriyle. */
    public static final List<String> DRINKS = List.of("kahve", "icecek");

    /**
     * Restoranın türü (harita ikonu) hesaplanırken sayılmayanlar: her menüde
     * bulunan eşlikçiler. Yalnız bunlardan oluşan menüde sayılır.
     */
    public static final List<String> COMPANIONS = List.of("kahve", "icecek", "atistirmalik");

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
