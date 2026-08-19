package com.foodboxd.api.repositories;

import com.foodboxd.api.entities.AppNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {

    List<AppNotification> findByRecipientUserIdOrderByCreatedAtDesc(Long userId);

    List<AppNotification> findByRecipientUserIdAndReadFalse(Long userId);

    long countByRecipientUserIdAndReadFalse(Long userId);
}
