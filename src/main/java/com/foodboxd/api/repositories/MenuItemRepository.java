package com.foodboxd.api.repositories;

import com.foodboxd.api.entities.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByRestaurant_RestaurantId(Long restaurantId);

    List<MenuItem> findByCategory_CategoryId(Long categoryId);

    Optional<MenuItem> findByRestaurant_RestaurantIdAndName(Long restaurantId, String name);

    boolean existsByRestaurant_RestaurantIdAndName(Long restaurantId, String name);

    List<MenuItem> findByNameContainingIgnoreCase(String name);
}
