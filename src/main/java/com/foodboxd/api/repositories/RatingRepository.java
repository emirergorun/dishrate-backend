package com.foodboxd.api.repositories;

import com.foodboxd.api.entities.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {

    Optional<Rating> findByUser_UserIdAndMenuItem_MenuItemId(Long userId, Long menuItemId);

    List<Rating> findByMenuItem_MenuItemId(Long menuItemId);

    List<Rating> findByUser_UserId(Long userId);

    boolean existsByUser_UserIdAndMenuItem_MenuItemId(Long userId, Long menuItemId);

    long countByMenuItem_MenuItemId(Long menuItemId);

    @Query("SELECT AVG(r.score) FROM Rating r WHERE r.menuItem.menuItemId = :menuItemId")
    Optional<BigDecimal> calculateAverageScoreByMenuItemId(@Param("menuItemId") Long menuItemId);

    /** Hesap silme: kullanıcının bütün kayıtları tek sorguda. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Rating r WHERE r.user.userId = :userId")
    void deleteAllOfUser(@Param("userId") Long userId);
}
