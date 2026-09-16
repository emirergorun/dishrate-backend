package com.foodboxd.api.repositories;

import com.foodboxd.api.entities.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    List<Restaurant> findByNameContainingIgnoreCase(String name);

    /**
     * Türkçe karakter ve harf büyüklüğünden bağımsız ad araması
     * (bkz. {@code AramaMetni}). "beto", "BETO" ve "Betö" aynı restoranı bulur.
     */
    @Query(value = """
            SELECT r.* FROM restaurants r
            WHERE lower(translate(r.name, :kaynak, :hedef)) LIKE :kalip
            ORDER BY r.name
            LIMIT :enFazla
            """, nativeQuery = true)
    List<Restaurant> searchByNormalizedName(@Param("kalip") String kalip,
                                            @Param("kaynak") String kaynak,
                                            @Param("hedef") String hedef,
                                            @Param("enFazla") int enFazla);

    List<Restaurant> findByAddress_City(String city);

    /** Bir kullanıcının sahibi olduğu restoranlar (owner paneli). */
    List<Restaurant> findByOwnerUserId(Long userId);

    /** Sahte veri sayacı — tohumlayıcının tekrar çalışmasını engeller. */
    long countBySeedTag(String seedTag);

    List<Restaurant> findBySeedTag(String seedTag);
}
