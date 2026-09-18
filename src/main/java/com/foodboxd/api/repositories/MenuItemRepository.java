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

    List<MenuItem> findByCategory_NameIgnoreCase(String categoryName);

    Optional<MenuItem> findByRestaurant_RestaurantIdAndName(Long restaurantId, String name);

    boolean existsByRestaurant_RestaurantIdAndName(Long restaurantId, String name);

    List<MenuItem> findByNameContainingIgnoreCase(String name);

    /**
     * Arama ekranı: yemek adı, restoran adı ya da kategori adı eşleşen
     * yemekler, en yüksek puanlı önce. Türkçe karakter ve harf büyüklüğü
     * yok sayılır (bkz. {@code SearchText}).
     *
     * <p>Önceden yalnızca yemek adına bakılıyordu: "Beto" yazınca Beto
     * Burger'ın hiçbir yemeğinin adında "beto" geçmediği için sonuç çıkmıyordu.
     */
    @Query(value = """
            SELECT mi.* FROM menu_items mi
              JOIN restaurants r ON r.restaurant_id = mi.restaurant_id
              LEFT JOIN categories c ON c.category_id = mi.category_id
            WHERE lower(translate(mi.name, :source, :target)) LIKE :pattern
               OR lower(translate(r.name, :source, :target)) LIKE :pattern
               OR lower(translate(coalesce(c.name, ''), :source, :target)) LIKE :pattern
            ORDER BY mi.average_rating DESC, mi.menu_item_id DESC
            LIMIT :maxResults
            """, nativeQuery = true)
    List<MenuItem> search(@Param("pattern") String pattern,
                          @Param("source") String source,
                          @Param("target") String target,
                          @Param("maxResults") int maxResults);

    /** Restoran başına kategori sayıları: [restaurantId, kategori adı, adet]. */
    @Query("""
            SELECT mi.restaurant.restaurantId, c.name, COUNT(mi)
            FROM MenuItem mi JOIN mi.category c
            GROUP BY mi.restaurant.restaurantId, c.name
            """)
    List<Object[]> countCategoriesPerRestaurant();

    /** Tek restoranın kategori sayıları: [kategori adı, adet]. */
    @Query("""
            SELECT c.name, COUNT(mi)
            FROM MenuItem mi JOIN mi.category c
            WHERE mi.restaurant.restaurantId = :restaurantId
            GROUP BY c.name
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
     * <p>{@code catCount} parametresi JPQL'in boş koleksiyonla baş edememesi
     * yüzünden var: 0 ise kategori süzgeci hiç uygulanmaz.
     *
     * <p>İl/ilçe "yok" anlamında {@code null} değil boş metin alır: Hibernate
     * tipsiz {@code null}'u Postgres'e bytea olarak bağlıyor ve
     * {@code LOWER(bytea)} sorguyu patlatıyor.
     */
    @Query("""
            SELECT mi FROM MenuItem mi
              JOIN mi.restaurant r
              JOIN r.address a
            WHERE (:city = '' OR LOWER(a.city) = LOWER(:city))
              AND (:district = '' OR LOWER(a.district) = LOWER(:district))
              AND (:catCount = 0 OR mi.category.name IN :categories)
              AND mi.averageRating >= :minRating
            """)
    Page<MenuItem> findForFeed(@Param("city") String city,
                               @Param("district") String district,
                               @Param("categories") Collection<String> categories,
                               @Param("catCount") int catCount,
                               @Param("minRating") BigDecimal minRating,
                               Pageable pageable);
}
