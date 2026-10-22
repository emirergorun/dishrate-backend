package com.foodboxd.api.services.importer;

import java.util.List;

/**
 * İçe aktarmanın raporu (2.11b). Önizlemede ne olacağını, uygulamada ne
 * olduğunu anlatır. Hata varsa hiçbir şey yazılmaz ({@code applied=false}).
 *
 * @param dryRun     önizleme mi
 * @param applied    veritabanına yazıldı mı
 * @param rows       okunan veri satırı (başlık hariç)
 * @param restaurants restoran sayıları
 * @param menuItems  yemek sayıları
 * @param photos     kaydedilen (önizlemede kaydedilecek) fotoğraf sayısı
 * @param errors     satır satır hatalar; biri bile varsa yazma yapılmaz
 * @param warnings   yazmayı engellemeyen notlar
 */
public record CatalogImportReport(
        boolean dryRun,
        boolean applied,
        int rows,
        Counts restaurants,
        Counts menuItems,
        int photos,
        List<Issue> errors,
        List<Issue> warnings) {

    public record Counts(int created, int updated, int unchanged) {}

    /** {@code row}: tablodaki satır numarası (başlık 1. satır); 0 = dosyanın geneli. */
    public record Issue(int row, String column, String message) {}
}
