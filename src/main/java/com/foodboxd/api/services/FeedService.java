package com.foodboxd.api.services;

import com.foodboxd.api.dtos.responses.FeedSectionResponse;
import com.foodboxd.api.dtos.responses.MenuItemResponse;
import com.foodboxd.api.entities.MenuItem;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.repositories.MenuItemRepository;
import com.foodboxd.api.utils.CategorySlug;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Keşfet akışı.
 *
 * <p>Bölümler burada tanımlı, çünkü hepsi aynı süzgeç + sıralama kalıbının
 * varyasyonu. Eskiden istemci tüm menüyü indirip bu ayrımı kendisi yapıyordu;
 * katalog büyüdükçe bu her açılışta yüz binlerce baytlık bir indirme oluyordu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedService {

    private final MenuItemRepository menuItemRepository;
    private final MenuItemService menuItemService;

    /**
     * Bir bölümün süzgeç ve sıralama tanımı.
     *
     * @param atla sıralamanın başından atlanan kayıt sayısı
     */
    private record Section(String key, List<String> categories,
                         BigDecimal minRating, Sort sort, int skip) {}

    private static final Sort BY_RATING =
            Sort.by(Sort.Direction.DESC, "averageRating").and(Sort.by(Sort.Direction.DESC, "menuItemId"));
    private static final Sort BY_NEWEST = Sort.by(Sort.Direction.DESC, "menuItemId");

    private static final BigDecimal ZERO_RATING = BigDecimal.ZERO;
    private static final BigDecimal HIGH_RATING = BigDecimal.valueOf(4.5);

    /**
     * "Herkes denemek istiyor" en iyilerin ilk bu kadarını atlar, keşfetteki
     * "en iyiler" şeridiyle çakışmasın.
     *
     * <p>Önceden bu, "ikinci sayfa" olarak tanımlıydı. Sayfa boyutu keşfette
     * 10, "Tümünü gör"de 20 olunca ikinci sayfa iki ekranda farklı yerden
     * başlıyordu: şeritte 11–20. sıradakiler, açılan listede 21–40.
     * sıradakiler görünüyordu.
     */
    private static final int TOP_RATED_STRIP_SIZE = 10;

    private static final List<Section> SECTIONS = List.of(
            new Section("top-rated", List.of(), ZERO_RATING, BY_RATING, 0),
            new Section("weekly", List.of(), HIGH_RATING, BY_NEWEST, 0),
            new Section("most-wanted", List.of(), ZERO_RATING, BY_RATING, TOP_RATED_STRIP_SIZE),
            // Kategoriler anahtarla (2.10b): ad değişse de bölümler bozulmaz.
            // Eski listedeki "Salata" hiç var olmayan bir kategoriydi, çıkarıldı.
            // 22 Ekim'de 30 kategoriye göre yeniden dağıtıldı.
            new Section("cheat-meal",
                    List.of("burger", "pizza", "doner", "pide-lahmacun", "makarna", "sokak-lezzeti",
                            "atistirmalik", "sandvic", "meksika", "tatli"),
                    ZERO_RATING, BY_RATING, 0),
            new Section("healthy", List.of("vegan", "salata", "balik", "corba", "meze"),
                    ZERO_RATING, BY_RATING, 0),
            new Section("hidden-gems",
                    List.of("meze", "asya", "vegan", "tavuk", "ev-yemegi", "manti", "kofte", "ortadogu", "firin"),
                    ZERO_RATING, BY_RATING, 0));

    /** İlçede bu sayıdan az sonuç varsa il geneline düşülür. */
    private static final int DISTRICT_THRESHOLD = 4;

    /**
     * Tüm bölümler, her biri en fazla {@code limit} öğe.
     *
     * <p>İlçe seçiliyken sonuç çok azsa il geneline düşülür — kullanıcı boş
     * ekranla karşılaşmasın. Bu karar tüm bölümler için birlikte verilir,
     * yoksa bazı bölümler ilçeden bazıları ilden gelip liste tutarsızlaşır.
     */
    @Transactional(readOnly = true)
    public List<FeedSectionResponse> feed(String city, String district, String category, int limit) {
        String cityKey = clean(city);
        String categoryKey = menuItemService.resolveCategorySlug(category);
        String districtKey = hasEnoughInDistrict(cityKey, clean(district), categoryKey) ? clean(district) : "";
        if (!clean(district).isEmpty() && districtKey.isEmpty()) {
            log.debug("İlçede yeterli içerik yok ({}), il geneline düşüldü.", district);
        }
        return SECTIONS.stream()
                .map(b -> findSection(b, cityKey, districtKey, categoryKey, limit))
                .filter(s -> !s.getItems().isEmpty())
                .toList();
    }

    /**
     * Tek bir bölümün devamı — "Tümünü gör" ekranı için sayfalı.
     */
    @Transactional(readOnly = true)
    public List<MenuItemResponse> section(String key, String city, String district,
                                          String category, int page, int size) {
        Section found = SECTIONS.stream()
                .filter(b -> b.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Bilinmeyen bölüm: " + key));

        String cityKey = clean(city);
        String categoryKey = menuItemService.resolveCategorySlug(category);
        String districtKey = hasEnoughInDistrict(cityKey, clean(district), categoryKey) ? clean(district) : "";
        long offset = found.skip() + (long) page * size;
        return query(found, cityKey, districtKey, categoryKey, offset, size)
                .map(menuItemService::toResponse)
                .toList();
    }

    // ── İç yardımcılar ────────────────────────────────────────────────────────

    private FeedSectionResponse findSection(Section b, String cityKey, String districtKey,
                                           String categoryKey, int limit) {
        Page<MenuItem> result = query(b, cityKey, districtKey, categoryKey, b.skip(), limit);
        return FeedSectionResponse.builder()
                .key(b.key())
                .items(result.map(menuItemService::toResponse).toList())
                .hasMore(result.getTotalElements() > (long) b.skip() + limit)
                .districtApplied(!districtKey.isEmpty())
                .build();
    }

    /**
     * Seçili kategori çipi bölümün kendi kategorileriyle kesiştirilir:
     * "burger" seçiliyken "Diyet dostu" bölümü hiç sorgulanmadan boş döner.
     */
    private Page<MenuItem> query(Section b, String cityKey, String districtKey, String categoryKey,
                                   long offset, int pageSize) {
        List<String> categories;
        if (categoryKey.isEmpty()) {
            categories = b.categories();
        } else if (b.categories().isEmpty() || b.categories().contains(categoryKey)) {
            categories = List.of(categoryKey);
        } else {
            return new PageImpl<>(List.of());
        }
        // Çip seçili değilken içecekler genel bölümlere girmez (22 Ekim): Keşfet
        // yemek için; kahve ve içecekler yalnız kendi çipleriyle ve aramada.
        List<String> excluded = categoryKey.isEmpty() ? CategorySlug.DRINKS : List.of();
        return menuItemRepository.findForFeed(
                cityKey, districtKey,
                // JPQL boş koleksiyon kabul etmiyor; catCount=0 ile devre dışı
                categories.isEmpty() ? List.of("") : categories,
                categories.size(),
                excluded.isEmpty() ? List.of("") : excluded,
                excluded.size(),
                b.minRating(),
                new OffsetRange(offset, pageSize, b.sort()));
    }

    private boolean hasEnoughInDistrict(String cityKey, String districtKey, String categoryKey) {
        if (districtKey.isEmpty()) return false;
        List<String> categories = categoryKey.isEmpty() ? List.of("") : List.of(categoryKey);
        List<String> excluded = categoryKey.isEmpty() ? CategorySlug.DRINKS : List.of("");
        long count = menuItemRepository.findForFeed(
                cityKey, districtKey, categories, categoryKey.isEmpty() ? 0 : 1,
                excluded, categoryKey.isEmpty() ? excluded.size() : 0, ZERO_RATING,
                PageRequest.of(0, 1)).getTotalElements();
        return count >= DISTRICT_THRESHOLD;
    }

    /** Sorguya "süzgeç yok" anlamında null yerine boş metin gider. */
    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * Sayfa numarası yerine doğrudan kayıt atlayan {@link Pageable}.
     * {@link PageRequest} yalnızca sayfa boyutunun katlarını atlayabiliyor.
     */
    private record OffsetRange(long offset, int size, Sort sort) implements Pageable {
        @Override public int getPageNumber() { return (int) (offset / size); }
        @Override public int getPageSize() { return size; }
        @Override public long getOffset() { return offset; }
        @Override public Sort getSort() { return sort; }
        @Override public Pageable next() { return new OffsetRange(offset + size, size, sort); }
        @Override public Pageable previousOrFirst() {
            return offset < size ? first() : new OffsetRange(offset - size, size, sort);
        }
        @Override public Pageable first() { return new OffsetRange(0, size, sort); }
        @Override public Pageable withPage(int pageNumber) {
            return new OffsetRange((long) pageNumber * size, size, sort);
        }
        @Override public boolean hasPrevious() { return offset > 0; }
    }
}
