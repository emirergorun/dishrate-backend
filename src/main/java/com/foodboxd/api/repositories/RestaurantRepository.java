package com.foodboxd.api.repositories;

import com.foodboxd.api.entities.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    List<Restaurant> findByNameContainingIgnoreCase(String name);

    /**
     * Türkçe karakter ve harf büyüklüğünden bağımsız ad araması
     * (bkz. {@code SearchText}). "beto", "BETO" ve "Betö" aynı restoranı bulur.
     */
    @Query(value = """
            SELECT r.* FROM restaurants r
            WHERE lower(translate(r.name, :source, :target)) LIKE :pattern
            ORDER BY r.name
            LIMIT :maxResults
            """, nativeQuery = true)
    List<Restaurant> searchByNormalizedName(@Param("pattern") String pattern,
                                            @Param("source") String source,
                                            @Param("target") String target,
                                            @Param("maxResults") int maxResults);

    List<Restaurant> findByAddress_City(String city);

    /** Bir kullanıcının sahibi olduğu restoranlar (owner paneli). */
    List<Restaurant> findByOwnerUserId(Long userId);

    /** Sahte veri sayacı — tohumlayıcının tekrar çalışmasını engeller. */
    long countBySeedTag(String seedTag);

    List<Restaurant> findBySeedTag(String seedTag);

    /** Hesap silme: sahibi silinen restoran sahipsiz kalır, restoran silinmez. */
    @Modifying
    @Query("UPDATE Restaurant r SET r.owner = null WHERE r.owner.userId = :userId")
    void clearOwner(@Param("userId") Long userId);
}
