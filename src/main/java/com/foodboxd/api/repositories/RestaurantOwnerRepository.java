package com.foodboxd.api.repositories;

import com.foodboxd.api.entities.OwnerRole;
import com.foodboxd.api.entities.RestaurantOwner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantOwnerRepository extends JpaRepository<RestaurantOwner, Long> {

    List<RestaurantOwner> findByRestaurantRestaurantId(Long restaurantId);

    // Bir kullanıcının sahip olduğu tüm restoran kayıtları
    List<RestaurantOwner> findByUserUserId(Long userId);

    Optional<RestaurantOwner> findByRestaurantRestaurantIdAndUserUserId(Long restaurantId, Long userId);

    boolean existsByRestaurantRestaurantIdAndRole(Long restaurantId, OwnerRole role);
}
