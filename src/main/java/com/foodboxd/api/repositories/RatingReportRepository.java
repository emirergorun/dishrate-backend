package com.foodboxd.api.repositories;

import com.foodboxd.api.entities.RatingReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface RatingReportRepository extends JpaRepository<RatingReport, Long> {

    boolean existsByRating_RatingIdAndReporter_UserId(Long ratingId, Long reporterId);

    long countByRating_RatingId(Long ratingId);

    /** Gizleme eşiği ve mail kuralı yalnız çözülmemiş bildirimleri sayar (2.11a). */
    long countByRating_RatingIdAndResolvedAtIsNull(Long ratingId);

    @Query("SELECT r FROM RatingReport r JOIN FETCH r.reporter WHERE r.rating.ratingId = :ratingId "
            + "AND r.resolvedAt IS NULL ORDER BY r.createdAt")
    List<RatingReport> findOpenByRating(@Param("ratingId") Long ratingId);

    @Query("SELECT r FROM RatingReport r JOIN FETCH r.reporter WHERE r.rating.ratingId = :ratingId "
            + "ORDER BY r.createdAt")
    List<RatingReport> findAllByRating(@Param("ratingId") Long ratingId);

    /** Değerlendirmenin açık bildirimlerini "çözüldü" işaretle. */
    @Modifying
    @Query("UPDATE RatingReport r SET r.resolvedAt = :now WHERE r.rating.ratingId = :ratingId "
            + "AND r.resolvedAt IS NULL")
    int resolveAll(@Param("ratingId") Long ratingId, @Param("now") Instant now);

    @Modifying
    void deleteByRating_RatingIdAndReporter_UserId(Long ratingId, Long reporterId);

    /** Kullanıcının bildirdiği değerlendirmeler — ona bir daha gösterilmez. */
    @Query("SELECT r.rating.ratingId FROM RatingReport r WHERE r.reporter.userId = :userId")
    Set<Long> findRatingIdsReportedBy(@Param("userId") Long userId);
}
