package com.foodboxd.api.services;

import com.foodboxd.api.dtos.responses.FeedSectionResponse;
import com.foodboxd.api.dtos.responses.MenuItemResponse;
import com.foodboxd.api.entities.MenuItem;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.repositories.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
            new Bolum("healthy", List.of("Vegan", "Salata", "Kahvaltı", "Meze"), SIFIR, PUANA_GORE, 0),
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
    public List<FeedSectionResponse> feed(String city, String district, String category, int limit) {
        String il = temiz(city);
        String kategori = temiz(category);
        String ilce = ilceYeterliMi(il, temiz(district), kategori) ? temiz(district) : "";
        if (!temiz(district).isEmpty() && ilce.isEmpty()) {
            log.debug("İlçede yeterli içerik yok ({}), il geneline düşüldü.", district);
        }
        return BOLUMLER.stream()
                .map(b -> bolumGetir(b, il, ilce, kategori, limit))
                .filter(s -> !s.getItems().isEmpty())
                .toList();
    }

    /**
     * Tek bir bölümün devamı — "Tümünü gör" ekranı için sayfalı.
     */
    @Transactional(readOnly = true)
    public List<MenuItemResponse> section(String key, String city, String district,
                                          String category, int page, int size) {
        Bolum bolum = BOLUMLER.stream()
                .filter(b -> b.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Bilinmeyen bölüm: " + key));

        String il = temiz(city);
        String kategori = temiz(category);
        String ilce = ilceYeterliMi(il, temiz(district), kategori) ? temiz(district) : "";
        // Bölümün kendi başlangıç sayfası korunur: "most-wanted" ilk sayfayı atlar.
        return sorgula(bolum, il, ilce, kategori, bolum.sayfa() + page, size)
                .map(menuItemService::toResponse)
                .toList();
    }

    // ── İç yardımcılar ────────────────────────────────────────────────────────

    private FeedSectionResponse bolumGetir(Bolum b, String il, String ilce,
                                           String kategori, int limit) {
        Page<MenuItem> sayfa = sorgula(b, il, ilce, kategori, b.sayfa(), limit);
        return FeedSectionResponse.builder()
                .key(b.key())
                .items(sayfa.map(menuItemService::toResponse).toList())
                .hasMore(sayfa.getTotalElements() > (long) (b.sayfa() + 1) * limit)
                .build();
    }

    /**
     * Seçili kategori çipi bölümün kendi kategorileriyle kesiştirilir:
     * "Burger" seçiliyken "Diyet dostu" bölümü hiç sorgulanmadan boş döner.
     */
    private Page<MenuItem> sorgula(Bolum b, String il, String ilce, String kategori,
                                   int sayfa, int boyut) {
        List<String> kategoriler;
        if (kategori.isEmpty()) {
            kategoriler = b.kategoriler();
        } else if (b.kategoriler().isEmpty() || b.kategoriler().contains(kategori)) {
            kategoriler = List.of(kategori);
        } else {
            return new PageImpl<>(List.of());
        }
        return menuItemRepository.findForFeed(
                il, ilce,
                // JPQL boş koleksiyon kabul etmiyor; catCount=0 ile devre dışı
                kategoriler.isEmpty() ? List.of("") : kategoriler,
                kategoriler.size(),
                b.enAzPuan(),
                PageRequest.of(sayfa, boyut, b.sirala()));
    }

    private boolean ilceYeterliMi(String il, String ilce, String kategori) {
        if (ilce.isEmpty()) return false;
        List<String> kategoriler = kategori.isEmpty() ? List.of("") : List.of(kategori);
        long adet = menuItemRepository.findForFeed(
                il, ilce, kategoriler, kategori.isEmpty() ? 0 : 1, SIFIR,
                PageRequest.of(0, 1)).getTotalElements();
        return adet >= ILCE_ESIGI;
    }

    /** Sorguya "süzgeç yok" anlamında null yerine boş metin gider. */
    private static String temiz(String s) {
        return s == null ? "" : s.trim();
    }
}
