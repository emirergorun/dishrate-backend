package com.foodboxd.api.services;

import com.foodboxd.api.dtos.responses.FeedSectionResponse;
import com.foodboxd.api.dtos.responses.MenuItemResponse;
import com.foodboxd.api.entities.MenuItem;
import com.foodboxd.api.repositories.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
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

    /** Bir bölümün süzgeç ve sıralama tanımı. */
    private record Bolum(String key, List<String> kategoriler,
                         BigDecimal enAzPuan, Sort sirala, int sayfa) {}

    private static final Sort PUANA_GORE =
            Sort.by(Sort.Direction.DESC, "averageRating").and(Sort.by(Sort.Direction.DESC, "menuItemId"));
    private static final Sort YENIYE_GORE = Sort.by(Sort.Direction.DESC, "menuItemId");

    private static final BigDecimal SIFIR = BigDecimal.ZERO;
    private static final BigDecimal YUKSEK = BigDecimal.valueOf(4.5);

    private static final List<Bolum> BOLUMLER = List.of(
            new Bolum("top-rated", List.of(), SIFIR, PUANA_GORE, 0),
            new Bolum("weekly", List.of(), YUKSEK, YENIYE_GORE, 0),
            // "Herkes denemek istiyor": en tepedekilerin hemen ardındakiler —
            // aynı sıralamanın ikinci sayfası, böylece ilk bölümle çakışmaz.
            new Bolum("most-wanted", List.of(), SIFIR, PUANA_GORE, 1),
            new Bolum("cheat-meal",
                    List.of("Burger", "Pizza", "Tatlı", "Kebap", "İtalyan", "Noodle", "Sandviç"),
                    SIFIR, PUANA_GORE, 0),
            new Bolum("healthy", List.of("Vegan", "Kahvaltı", "Meze"), SIFIR, PUANA_GORE, 0),
            new Bolum("hidden-gems", List.of("Meze", "Noodle", "Vegan", "Tavuk"),
                    SIFIR, PUANA_GORE, 0));

    /** İlçede bu sayıdan az sonuç varsa il geneline düşülür. */
    private static final int ILCE_ESIGI = 4;

    /**
     * Tüm bölümler, her biri en fazla {@code limit} öğe.
     *
     * <p>İlçe seçiliyken sonuç çok azsa il geneline düşülür — kullanıcı boş
     * ekranla karşılaşmasın. Bu karar tüm bölümler için birlikte verilir,
     * yoksa bazı bölümler ilçeden bazıları ilden gelip liste tutarsızlaşır.
     */
    @Transactional(readOnly = true)
    public List<FeedSectionResponse> feed(String city, String district, int limit) {
        String etkinIlce = ilceYeterliMi(city, district) ? district : null;
        if (district != null && etkinIlce == null) {
            log.debug("İlçede yeterli içerik yok ({}), il geneline düşüldü.", district);
        }
        return BOLUMLER.stream()
                .map(b -> bolumGetir(b, city, etkinIlce, limit))
                .filter(s -> !s.getItems().isEmpty())
                .toList();
    }

    /**
     * Tek bir bölümün devamı — "Tümünü gör" ekranı için sayfalı.
     */
    @Transactional(readOnly = true)
    public List<MenuItemResponse> section(String key, String city, String district,
                                          int page, int size) {
        Bolum bolum = BOLUMLER.stream()
                .filter(b -> b.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Bilinmeyen bölüm: " + key));

        String etkinIlce = ilceYeterliMi(city, district) ? district : null;
        // Bölümün kendi başlangıç sayfası korunur: "most-wanted" ilk sayfayı atlar.
        return sorgula(bolum, city, etkinIlce, bolum.sayfa() + page, size)
                .map(menuItemService::toResponse)
                .toList();
    }

    // ── İç yardımcılar ────────────────────────────────────────────────────────

    private FeedSectionResponse bolumGetir(Bolum b, String city, String district, int limit) {
        Page<MenuItem> sayfa = sorgula(b, city, district, b.sayfa(), limit);
        return FeedSectionResponse.builder()
                .key(b.key())
                .items(sayfa.map(menuItemService::toResponse).toList())
                .hasMore(sayfa.getTotalElements() > (long) (b.sayfa() + 1) * limit)
                .build();
    }

    private Page<MenuItem> sorgula(Bolum b, String city, String district, int sayfa, int boyut) {
        Collection<String> kategoriler = b.kategoriler().isEmpty()
                ? List.of("")            // JPQL boş koleksiyon kabul etmiyor; catCount=0 ile devre dışı
                : b.kategoriler();
        return menuItemRepository.findForFeed(
                bos(city) ? null : city,
                bos(district) ? null : district,
                kategoriler,
                b.kategoriler().size(),
                b.enAzPuan(),
                PageRequest.of(sayfa, boyut, b.sirala()));
    }

    private boolean ilceYeterliMi(String city, String district) {
        if (bos(district)) return false;
        long adet = menuItemRepository.findForFeed(
                bos(city) ? null : city, district, List.of(""), 0, SIFIR,
                PageRequest.of(0, 1)).getTotalElements();
        return adet >= ILCE_ESIGI;
    }

    private static boolean bos(String s) {
        return s == null || s.isBlank();
    }

    /** İstemcinin gönderebileceği bölüm anahtarları. */
    public static List<String> keys() {
        return BOLUMLER.stream().map(Bolum::key).toList();
    }
}
