package com.foodboxd.api.repositories;

import com.foodboxd.api.entities.AppNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {

    List<AppNotification> findByRecipientUserIdOrderByCreatedAtDesc(Long userId);

    List<AppNotification> findByRecipientUserIdAndReadFalse(Long userId);

    long countByRecipientUserIdAndReadFalse(Long userId);

    /** Hesap silme: kullanıcının bütün kayıtları tek sorguda. */
    @Modifying
    @Query("DELETE FROM AppNotification n WHERE n.recipient.userId = :userId")
    void deleteAllOfUser(@Param("userId") Long userId);
}
