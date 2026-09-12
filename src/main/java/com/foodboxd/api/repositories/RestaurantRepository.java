package com.foodboxd.api.repositories;

import com.foodboxd.api.entities.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    List<Restaurant> findByNameContainingIgnoreCase(String name);

    List<Restaurant> findByAddress_City(String city);

    /** Bir kullanıcının sahibi olduğu restoranlar (owner paneli). */
    List<Restaurant> findByOwnerUserId(Long userId);

    /** Sahte veri sayacı — tohumlayıcının tekrar çalışmasını engeller. */
    long countBySeedTag(String seedTag);
}
