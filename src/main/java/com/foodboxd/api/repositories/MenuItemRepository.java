package com.foodboxd.api.repositories;

import com.foodboxd.api.entities.MenuItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByRestaurant_RestaurantId(Long restaurantId);

    List<MenuItem> findByCategory_CategoryId(Long categoryId);

    List<MenuItem> findByCategory_Slug(String slug);

    Optional<MenuItem> findByRestaurant_RestaurantIdAndName(Long restaurantId, String name);

    boolean existsByRestaurant_RestaurantIdAndName(Long restaurantId, String name);

    List<MenuItem> findByNameContainingIgnoreCase(String name);

    /**
     * Arama ekranı: yemek adı ya da kategori adı eşleşen yemekler, en yüksek
     * puanlı önce. Türkçe karakter ve harf büyüklüğü yok sayılır (bkz.
     * {@code SearchText}).
     *
     * <p>Restoran adına burada bakılmaz; onu {@code SearchService} ayrı arıyor.
     * Önceden bakılıyordu ve "bun lab" yazınca Bun Lab'ın bütün yemekleri
     * "eşleşen yemek" sayılıyordu (24 Eylül).
     */
    @Query(value = """
            SELECT mi.* FROM menu_items mi
              LEFT JOIN categories c ON c.category_id = mi.category_id
            WHERE lower(translate(mi.name, :source, :target)) LIKE :pattern
               OR lower(translate(coalesce(c.name, ''), :source, :target)) LIKE :pattern
            ORDER BY mi.average_rating DESC, mi.menu_item_id DESC
            LIMIT :maxResults
            """, nativeQuery = true)
    List<MenuItem> search(@Param("pattern") String pattern,
                          @Param("source") String source,
                          @Param("target") String target,
                          @Param("maxResults") int maxResults);

    /** Restoran başına kategori sayıları: [restaurantId, kategori adı, anahtar, adet]. */
    @Query("""
            SELECT mi.restaurant.restaurantId, c.name, c.slug, COUNT(mi)
            FROM MenuItem mi JOIN mi.category c
            GROUP BY mi.restaurant.restaurantId, c.name, c.slug
            """)
    List<Object[]> countCategoriesPerRestaurant();

    /** Tek restoranın kategori sayıları: [kategori adı, anahtar, adet]. */
    @Query("""
            SELECT c.name, c.slug, COUNT(mi)
            FROM MenuItem mi JOIN mi.category c
            WHERE mi.restaurant.restaurantId = :restaurantId
            GROUP BY c.name, c.slug
            """)
    List<Object[]> countCategoriesOfRestaurant(@Param("restaurantId") Long restaurantId);

    @Query("SELECT mi FROM MenuItem mi JOIN FETCH mi.restaurant r WHERE r.seedTag = :tag")
    List<MenuItem> findBySeedTag(@Param("tag") String tag);

    /**
     * Keşfet bölümlerinin tek sorgusu: konuma ve kategoriye göre süzer,
     * sıralama ve sayfa boyutu {@link Pageable} ile verilir.
     *
     * <p>Daha önce istemci tüm yemekleri çekip bölümleri kendisi hesaplıyordu;
     * 1.400 öğede bu her açılışta yarım megabayt indirmek demekti.
     *
     * <p>Kategoriler anahtarla ({@code slug}) süzülür, ada bakılmaz (2.10b).
     *
     * <p>{@code catCount} parametresi JPQL'in boş koleksiyonla baş edememesi
     * yüzünden var: 0 ise kategori süzgeci hiç uygulanmaz. {@code excluded} /
     * {@code exCount} aynı mantıkla hariç tutulan kategoriler (çip seçili
     * değilken içecekler, 22 Ekim). Kategori {@code LEFT JOIN}: kategorisiz
     * yemek süzgeçsiz bölümlerden düşmesin.
     *
     * <p>İl/ilçe "yok" anlamında {@code null} değil boş metin alır: Hibernate
     * tipsiz {@code null}'u Postgres'e bytea olarak bağlıyor ve
     * {@code LOWER(bytea)} sorguyu patlatıyor.
     */
    @Query("""
            SELECT mi FROM MenuItem mi
              JOIN mi.restaurant r
              JOIN r.address a
              LEFT JOIN mi.category c
            WHERE (:city = '' OR LOWER(a.city) = LOWER(:city))
              AND (:district = '' OR LOWER(a.district) = LOWER(:district))
              AND (:catCount = 0 OR c.slug IN :categories)
              AND (:exCount = 0 OR c.slug IS NULL OR c.slug NOT IN :excluded)
              AND mi.averageRating >= :minRating
            """)
    Page<MenuItem> findForFeed(@Param("city") String city,
                               @Param("district") String district,
                               @Param("categories") Collection<String> categories,
                               @Param("catCount") int catCount,
                               @Param("excluded") Collection<String> excluded,
                               @Param("exCount") int exCount,
                               @Param("minRating") BigDecimal minRating,
                               Pageable pageable);
}
