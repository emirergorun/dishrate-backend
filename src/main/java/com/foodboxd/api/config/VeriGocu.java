package com.foodboxd.api.config;

import com.foodboxd.api.entities.Category;
import com.foodboxd.api.repositories.CategoryRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Var olan veritabanlarını güncel şemaya/tanımlara getiren küçük göçler.
 *
 * <p>Her adım tekrar çalıştırılabilir: yapılacak bir şey yoksa dokunmaz.
 * Tohumlayıcılardan sonra ({@link DataSeeder} = 2), sahte veriden önce
 * ({@link MockDataSeeder} = 4) çalışır — sahte veri güncel kategori adlarını
 * bulabilsin.
 */
@Slf4j
@Order(3)
@Component
@RequiredArgsConstructor
public class VeriGocu implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final EntityManager entityManager;
    private final YemekFotograflari fotograflar;

    /** 404 dönmeye başlayan eski Unsplash adresleri → yerine fotoğrafı konacak yemek. */
    private static final Map<String, String> KIRIK_FOTOGRAFLAR = Map.of(
            "https://images.unsplash.com/photo-1532550884612-72b92802ec04?w=400&h=300&fit=crop", "Izgara Tavuk",
            "https://images.unsplash.com/photo-1617196034183-421b4040ed20?w=400&h=300&fit=crop", "Somon Nigiri");

    @Override
    @Transactional
    public void run(String... args) {
        kebapKategorisiniTasi();
        for (String ad : List.of("Türk Mutfağı", "Ev Yemeği")) {
            if (!categoryRepository.existsByName(ad)) {
                categoryRepository.save(Category.builder().name(ad).build());
                log.info("Veri göçü: '{}' kategorisi eklendi.", ad);
            }
        }
        kirikFotograflariDegistir();
        puanSayilariniDoldur();
    }

    /**
     * "Kebap" kategorisi "Türk Mutfağı" oldu: lahmacun, pide ve tantuni de
     * kebap sayılıyordu.
     */
    private void kebapKategorisiniTasi() {
        Optional<Category> kebap = categoryRepository.findByName("Kebap");
        if (kebap.isEmpty()) return;

        Optional<Category> turk = categoryRepository.findByName("Türk Mutfağı");
        if (turk.isEmpty()) {
            kebap.get().setName("Türk Mutfağı");
            categoryRepository.save(kebap.get());
            log.info("Veri göçü: 'Kebap' kategorisi 'Türk Mutfağı' olarak yeniden adlandırıldı.");
            return;
        }
        int tasinan = entityManager.createNativeQuery(
                        "UPDATE menu_items SET category_id = :yeni WHERE category_id = :eski")
                .setParameter("yeni", turk.get().getCategoryId())
                .setParameter("eski", kebap.get().getCategoryId())
                .executeUpdate();
        categoryRepository.delete(kebap.get());
        log.info("Veri göçü: {} yemek 'Kebap'tan 'Türk Mutfağı'na taşındı.", tasinan);
    }

    private void kirikFotograflariDegistir() {
        KIRIK_FOTOGRAFLAR.forEach((eski, yemek) -> {
            String yeni = fotograflar.sec(yemek, 0);
            if (yeni == null) return;
            int n = entityManager.createNativeQuery(
                            "UPDATE menu_items SET photo_url = :yeni WHERE photo_url = :eski")
                    .setParameter("yeni", yeni)
                    .setParameter("eski", eski)
                    .executeUpdate();
            if (n > 0) log.info("Veri göçü: {} yemeğin kırık fotoğrafı değiştirildi.", n);
        });
    }

    /** {@code rating_count} kolonu sonradan eklendi; eski satırlarda boş. */
    private void puanSayilariniDoldur() {
        int n = entityManager.createNativeQuery("""
                UPDATE menu_items m
                   SET rating_count = (SELECT COUNT(*) FROM ratings r
                                        WHERE r.menu_item_id = m.menu_item_id)
                 WHERE m.rating_count IS NULL
                """).executeUpdate();
        if (n > 0) log.info("Veri göçü: {} yemeğin puan sayısı dolduruldu.", n);
    }
}
