package com.foodboxd.api.repositories;

import com.foodboxd.api.entities.PasswordResetCode;
import com.foodboxd.api.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    /** Kullanıcının en yeni, henüz kullanılmamış kodu. */
    Optional<PasswordResetCode> findFirstByUserAndUsedAtIsNullOrderByCreatedAtDesc(User user);

    long countByUserAndCreatedAtAfter(User user, Instant since);

    /** Önceki kodları geçersiz kıl (yeni kod gönderilince). */
    @Modifying
    @Query("UPDATE PasswordResetCode c SET c.usedAt = :now WHERE c.user = :user AND c.usedAt IS NULL")
    int invalidateAll(@Param("user") User user, @Param("now") Instant now);

    /** O kullanıcının süresi geçmiş ya da kullanılmış eski kodları (saatlik sayaç için son bir saat kalır). */
    @Modifying
    @Query("DELETE FROM PasswordResetCode c WHERE c.user = :user AND c.createdAt < :before")
    int deleteOld(@Param("user") User user, @Param("before") Instant before);
}
