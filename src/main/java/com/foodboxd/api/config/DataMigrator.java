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
        backfillCategoryKeys();
        expandCategories();
        replaceBrokenPhotos();
        backfillRatingCounts();
        backfillMemberCodes();
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
     * Kategorilerin genişlemesi (22 Ekim, 13 → 30; liste
     * {@link CategorySlug#DEFAULT_ORDER}). Tekrar çalıştırılabilir:
     * <ol>
     *   <li>"Noodle" → "Asya" (anahtar da {@code asya}; yayın öncesi, eski
     *       yapıların "noodle"u {@code MenuItemService.resolveCategorySlug}'da
     *       çevrilir),</li>
     *   <li>listedeki bütün kategoriler var edilir — üretimde örnek veri kapalı,
     *       içe aktarma (2.11b) bunlara dayanıyor,</li>
     *   <li>"Türk Mutfağı"ndaki yemekler adlarına göre yeni kategorilere
     *       dağıtılır, kategori silinir,</li>
     *   <li>çip sırası listeye göre yeniden yazılır; listede olmayanlar sona.</li>
     * </ol>
     * Eski {@code migrateKebabCategory} ("Kebap" → "Türk Mutfağı") kaldırıldı:
     * yeni Kebap kategorisini her açılışta geri çevirirdi.
     */
    private void expandCategories() {
        mergePideLahmacun();
        categoryRepository.findBySlug("noodle").ifPresent(noodle -> {
            if (categoryRepository.findBySlug("asya").isEmpty()) {
                noodle.setName("Asya");
                noodle.setSlug("asya");
                categoryRepository.save(noodle);
                log.info("Veri göçü: 'Noodle' kategorisi 'Asya' oldu.");
            }
        });

        for (String name : CategorySlug.DEFAULT_ORDER) {
            if (!categoryRepository.existsByName(name)
                    && !categoryRepository.existsBySlug(CategorySlug.of(name))) {
                categoryRepository.save(Category.create(name, CategorySlug.defaultOrder(name)));
                log.info("Veri göçü: '{}' kategorisi eklendi.", name);
            }
        }

        categoryRepository.findBySlug("turk-mutfagi").ifPresent(turkish -> {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery(
                            "SELECT menu_item_id, name FROM menu_items WHERE category_id = :id")
                    .setParameter("id", turkish.getCategoryId())
                    .getResultList();
            Map<String, Long> ids = new java.util.HashMap<>();
            for (Category c : categoryRepository.findAll()) {
                if (c.getSlug() != null) ids.put(c.getSlug(), c.getCategoryId());
            }
            for (Object[] row : rows) {
                String target = turkishDishTarget((String) row[1]);
                entityManager.createNativeQuery(
                                "UPDATE menu_items SET category_id = :c WHERE menu_item_id = :m")
                        .setParameter("c", ids.get(target))
                        .setParameter("m", ((Number) row[0]).longValue())
                        .executeUpdate();
            }
            categoryRepository.delete(turkish);
            log.info("Veri göçü: 'Türk Mutfağı' bölündü, {} yemek yeni kategorilerine taşındı.", rows.size());
        });

        List<String> order = CategorySlug.DEFAULT_ORDER.stream().map(CategorySlug::of).toList();
        List<Category> all = new java.util.ArrayList<>(categoryRepository.findAll());
        all.sort(java.util.Comparator.comparing(
                (Category c) -> c.getSortOrder() == null ? Integer.MAX_VALUE : c.getSortOrder()));
        int next = order.size();
        int changed = 0;
        for (Category c : all) {
            int i = c.getSlug() == null ? -1 : order.indexOf(c.getSlug());
            int want = i >= 0 ? i + 1 : ++next;
            if (c.getSortOrder() == null || c.getSortOrder() != want) {
                c.setSortOrder(want);
                categoryRepository.save(c);
                changed++;
            }
        }
        if (changed > 0) {
            log.info("Veri göçü: {} kategorinin çip sırası güncellendi.", changed);
        }
    }

    /**
     * Pide ve Lahmacun tek kategori oldu (23 Ekim): "Pide & Lahmacun",
     * anahtar {@code pide-lahmacun} (boş veritabanında da {@code CategorySlug.of}
     * aynısını üretiyor). Pide yeniden adlandırılır, Lahmacun'un yemekleri ona
     * taşınıp kategori silinir.
     */
    private void mergePideLahmacun() {
        categoryRepository.findBySlug("pide").ifPresent(pide -> {
            if (categoryRepository.findBySlug("pide-lahmacun").isEmpty()) {
                pide.setName("Pide & Lahmacun");
                pide.setSlug("pide-lahmacun");
                categoryRepository.save(pide);
                log.info("Veri göçü: 'Pide' kategorisi 'Pide & Lahmacun' oldu.");
            }
        });
        categoryRepository.findBySlug("lahmacun").ifPresent(lahmacun -> {
            Category merged = categoryRepository.findBySlug("pide-lahmacun").orElseGet(() ->
                    categoryRepository.save(Category.create("Pide & Lahmacun",
                            CategorySlug.defaultOrder("Pide & Lahmacun"))));
            int moved = entityManager.createNativeQuery(
                            "UPDATE menu_items SET category_id = :to WHERE category_id = :from")
                    .setParameter("to", merged.getCategoryId())
                    .setParameter("from", lahmacun.getCategoryId())
                    .executeUpdate();
            categoryRepository.delete(lahmacun);
            log.info("Veri göçü: 'Lahmacun' 'Pide & Lahmacun'a katıldı, {} yemek taşındı.", moved);
        });
    }

    /** "Türk Mutfağı"ndaki bir yemeğin yeni kategorisi (anahtar), adına göre. */
    static String turkishDishTarget(String dishName) {
        String n = com.foodboxd.api.utils.SearchText.normalize(dishName);
        if (n.contains("tantuni") || n.contains("cig kofte") || n.contains("kokorec")
                || n.contains("midye")) return "sokak-lezzeti";
        if (n.contains("doner") || n.contains("iskender")) return "doner";
        if (n.contains("lahmacun") || n.contains("pide")) return "pide-lahmacun";
        if (n.contains("kofte")) return "kofte";
        if (n.contains("corba")) return "corba";
        return "kebap";
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
