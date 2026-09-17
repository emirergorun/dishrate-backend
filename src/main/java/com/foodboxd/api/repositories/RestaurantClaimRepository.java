package com.foodboxd.api.repositories;

import com.foodboxd.api.entities.ClaimStatus;
import com.foodboxd.api.entities.RestaurantClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantClaimRepository extends JpaRepository<RestaurantClaim, Long> {

    List<RestaurantClaim> findByStatusOrderByCreatedAtDesc(ClaimStatus status);

    List<RestaurantClaim> findAllByOrderByCreatedAtDesc();

    /** Kullanıcının kendi talepleri — durum takibi ekranı için. */
    List<RestaurantClaim> findByUserUserIdOrderByCreatedAtDesc(Long userId);

    /** Aynı restoran için aynı kullanıcının önceki talebi (varsa). */
    Optional<RestaurantClaim> findByUserUserIdAndRestaurantRestaurantId(
            Long userId, Long restaurantId);

    /** Bu restoran için hâlihazırda incelenmeyi bekleyen bir talep var mı? */
    boolean existsByRestaurantRestaurantIdAndStatus(Long restaurantId, ClaimStatus status);

    /** Hesap silme: kullanıcının bütün kayıtları tek sorguda. */
    @Modifying
    @Query("DELETE FROM RestaurantClaim c WHERE c.user.userId = :userId")
    void deleteAllOfUser(@Param("userId") Long userId);
}
