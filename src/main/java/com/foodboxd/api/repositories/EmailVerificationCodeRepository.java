package com.foodboxd.api.repositories;

import com.foodboxd.api.entities.EmailVerificationCode;
import com.foodboxd.api.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

    Optional<EmailVerificationCode> findFirstByUserAndUsedAtIsNullOrderByCreatedAtDesc(User user);

    long countByUserAndCreatedAtAfter(User user, Instant since);

    @Modifying
    @Query("UPDATE EmailVerificationCode c SET c.usedAt = :now WHERE c.user = :user AND c.usedAt IS NULL")
    int invalidateAll(@Param("user") User user, @Param("now") Instant now);

    /** Saatlik sayaçta işe yaramayan eski kodlar (kullanılmış ya da süresi geçmiş). */
    @Modifying
    @Query("DELETE FROM EmailVerificationCode c WHERE c.user = :user AND c.createdAt < :before AND (c.usedAt IS NOT NULL OR c.expiresAt < :now)")
    int deleteOld(@Param("user") User user, @Param("before") Instant before, @Param("now") Instant now);
}
