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
     * Keşfet bölümlerinin tek sorgusu: konuma ve kategoriye göre süzer,
     * sıralama ve sayfa boyutu {@link Pageable} ile verilir.
     *
     * <p>Daha önce istemci tüm yemekleri çekip bölümleri kendisi hesaplıyordu;
     * 1.400 öğede bu her açılışta yarım megabayt indirmek demekti.
     *
     * <p>{@code catCount} parametresi JPQL'in boş koleksiyonla baş edememesi
     * yüzünden var: 0 ise kategori süzgeci hiç uygulanmaz.
     */
    @Query("""
            SELECT mi FROM MenuItem mi
              JOIN mi.restaurant r
              JOIN r.address a
            WHERE (:city IS NULL OR LOWER(a.city) = LOWER(:city))
              AND (:district IS NULL OR LOWER(a.district) = LOWER(:district))
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
