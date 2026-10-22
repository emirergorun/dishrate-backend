package com.foodboxd.api.config;

import com.foodboxd.api.entities.Category;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.repositories.CategoryRepository;
import com.foodboxd.api.services.MemberCodeGenerator;
import jakarta.persistence.EntityManager;
import com.foodboxd.api.utils.CategorySlug;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
public class DataMigrator implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final EntityManager entityManager;
    private final DishPhotos photos;
    private final MemberCodeGenerator memberCodeGenerator;

    /** 404 dönmeye başlayan eski Unsplash adresleri → yerine fotoğrafı konacak yemek. */
    private static final Map<String, String> BROKEN_PHOTOS = Map.of(
            "https://images.unsplash.com/photo-1532550884612-72b92802ec04?w=400&h=300&fit=crop", "Izgara Tavuk",
            "https://images.unsplash.com/photo-1617196034183-421b4040ed20?w=400&h=300&fit=crop", "Somon Nigiri");

    @Override
    @Transactional
    public void run(String... args) {
        migrateKebabCategory();
        // Başlangıç kategorilerinin hepsi: üretimde örnek veri kapalı olduğu
        // için başka yerden gelmiyorlar, içe aktarma (2.11b) bunlara dayanıyor.
        for (String name : CategorySlug.DEFAULT_ORDER) {
            if (!categoryRepository.existsByName(name)
                    && !categoryRepository.existsBySlug(CategorySlug.of(name))) {
                categoryRepository.save(Category.create(name, CategorySlug.defaultOrder(name)));
                log.info("Veri göçü: '{}' kategorisi eklendi.", name);
            }
        }
        replaceBrokenPhotos();
        backfillRatingCounts();
        backfillMemberCodes();
        backfillCategoryKeys();
        markSeedAccountsVerified();
    }

    /**
     * E-posta doğrulama (2.8): mock hesaplar ve admin doğrulanmış sayılır
     * (gerçek posta kutuları yok; test verisinde hatırlatma çıkmasın). Gerçek
     * hesaplar dokunulmadan kalır.
     */
    private void markSeedAccountsVerified() {
        int marked = entityManager.createNativeQuery(
                        "update users set email_verified_at = now() where email_verified_at is null "
                                + "and (email like '%@mock.test' or role = 'ADMIN')")
                .executeUpdate();
        if (marked > 0) {
            log.info("Veri göçü: {} mock/admin hesap doğrulanmış sayıldı.", marked);
        }
    }

    /**
     * Kategori anahtarı ve sırası (2.10b). Anahtar yalnız boşsa yazılır:
     * bir kez verildikten sonra ad değişse de aynı kalmalı. Bilinen 13
     * kategori çip sırasını {@link CategorySlug#DEFAULT_ORDER}'dan alır,
     * diğerleri sona eklenir.
     */
    private void backfillCategoryKeys() {
        List<Category> all = categoryRepository.findAll();
        Set<String> used = new HashSet<>();
        int maxOrder = 0;
        for (Category c : all) {
            if (c.getSlug() != null) used.add(c.getSlug());
            if (c.getSortOrder() != null) maxOrder = Math.max(maxOrder, c.getSortOrder());
        }
        // Bilinen kategorilerin sırası 1–13'te ayrılmış; yeniler onların arkasına.
        maxOrder = Math.max(maxOrder, CategorySlug.DEFAULT_ORDER.size());
        int changed = 0;
        for (Category c : all) {
            boolean dirty = false;
            if (c.getSlug() == null) {
                String base = CategorySlug.of(c.getName());
                String slug = base;
                for (int n = 2; used.contains(slug); n++) slug = base + "-" + n;
                used.add(slug);
                c.setSlug(slug);
                dirty = true;
            }
            if (c.getSortOrder() == null) {
                Integer order = CategorySlug.defaultOrder(c.getName());
                c.setSortOrder(order != null ? order : ++maxOrder);
                dirty = true;
            }
            if (dirty) changed++;
        }
        if (changed > 0) {
            log.info("Veri göçü: {} kategoriye anahtar ve sıra verildi.", changed);
        }
    }

    /**
     * Adisyon (1.9): eski hesaplara üye kodu ve kayıt anı. Gerçek kayıt tarihi
     * bilinmediği için göç anı yazılır.
     */
    private void backfillMemberCodes() {
        List<User> users = entityManager.createQuery(
                "select u from User u where u.memberCode is null", User.class)
                .getResultList();
        Set<String> codes = new HashSet<>();
        for (User user : users) {
            String code = memberCodeGenerator.next(codes);
            codes.add(code);
            user.setMemberCode(code);
        }
        // created_at güncellenemez (updatable = false); boşları doğrudan SQL doldurur.
        int dated = entityManager.createNativeQuery(
                "update users set created_at = now() where created_at is null")
                .executeUpdate();
        if (!users.isEmpty() || dated > 0) {
            log.info("Veri göçü: {} kullanıcıya üye kodu, {} kullanıcıya kayıt anı verildi.",
                    users.size(), dated);
        }
    }

    /**
     * "Kebap" kategorisi "Türk Mutfağı" oldu: lahmacun, pide ve tantuni de
     * kebap sayılıyordu.
     */
    private void migrateKebabCategory() {
        Optional<Category> kebab = categoryRepository.findByName("Kebap");
        if (kebab.isEmpty()) return;

        Optional<Category> turkish = categoryRepository.findByName("Türk Mutfağı");
        if (turkish.isEmpty()) {
            kebab.get().setName("Türk Mutfağı");
            categoryRepository.save(kebab.get());
            log.info("Veri göçü: 'Kebap' kategorisi 'Türk Mutfağı' olarak yeniden adlandırıldı.");
            return;
        }
        int moved = entityManager.createNativeQuery(
                        "UPDATE menu_items SET category_id = :newValue WHERE category_id = :oldValue")
                .setParameter("newValue", turkish.get().getCategoryId())
                .setParameter("oldValue", kebab.get().getCategoryId())
                .executeUpdate();
        categoryRepository.delete(kebab.get());
        log.info("Veri göçü: {} yemek 'Kebap'tan 'Türk Mutfağı'na taşındı.", moved);
    }

    private void replaceBrokenPhotos() {
        BROKEN_PHOTOS.forEach((old, dish) -> {
            String fresh = photos.pick(dish, 0);
            if (fresh == null) return;
            int n = entityManager.createNativeQuery(
                            "UPDATE menu_items SET photo_url = :newValue WHERE photo_url = :oldValue")
                    .setParameter("newValue", fresh)
                    .setParameter("oldValue", old)
                    .executeUpdate();
            if (n > 0) log.info("Veri göçü: {} yemeğin kırık fotoğrafı değiştirildi.", n);
        });
    }

    /** {@code rating_count} kolonu sonradan eklendi; eski satırlarda boş. */
    private void backfillRatingCounts() {
        int n = entityManager.createNativeQuery("""
                UPDATE menu_items m
                   SET rating_count = (SELECT COUNT(*) FROM ratings r
                                        WHERE r.menu_item_id = m.menu_item_id)
                 WHERE m.rating_count IS NULL
                """).executeUpdate();
        if (n > 0) log.info("Veri göçü: {} yemeğin puan sayısı dolduruldu.", n);
    }
}
