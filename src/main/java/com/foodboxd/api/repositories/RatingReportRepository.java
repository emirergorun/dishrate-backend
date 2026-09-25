package com.foodboxd.api.repositories;

import com.foodboxd.api.entities.RatingReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface RatingReportRepository extends JpaRepository<RatingReport, Long> {

    boolean existsByRating_RatingIdAndReporter_UserId(Long ratingId, Long reporterId);

    long countByRating_RatingId(Long ratingId);

    @Modifying
    void deleteByRating_RatingIdAndReporter_UserId(Long ratingId, Long reporterId);

    /** Kullanıcının bildirdiği değerlendirmeler — ona bir daha gösterilmez. */
    @Query("SELECT r.rating.ratingId FROM RatingReport r WHERE r.reporter.userId = :userId")
    Set<Long> findRatingIdsReportedBy(@Param("userId") Long userId);
}
