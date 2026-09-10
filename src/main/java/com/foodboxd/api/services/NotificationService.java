package com.foodboxd.api.services;

import com.foodboxd.api.dtos.responses.NotificationResponse;
import com.foodboxd.api.entities.AppNotification;
import com.foodboxd.api.entities.MenuItem;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.repositories.AppNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final AppNotificationRepository notificationRepository;

    /**
     * Bir ürüne değerlendirme yapıldığında restoranın sahibine bildirim oluşturur.
     * Değerlendiren kişinin adı maskeli gelir; sahip kendi ürününü puanladıysa
     * bildirim gitmez. Restoran sahipsizse bildirilecek kimse yoktur.
     * FCM push eklendiğinde bu noktadan push da tetiklenecek.
     */
    @Transactional
    public void notifyOwnersNewRating(MenuItem menuItem, Long raterUserId,
                                      String maskedRaterName, BigDecimal score) {
        User owner = menuItem.getRestaurant().getOwner();
        if (owner == null || owner.getUserId().equals(raterUserId)) return;

        notificationRepository.save(AppNotification.builder()
                .recipient(owner)
                .type("NEW_RATING")
                .title("Yeni değerlendirme")
                .body(maskedRaterName + ", \"" + menuItem.getName() + "\" için "
                        + score.stripTrailingZeros().toPlainString() + " puan verdi.")
                .menuItemId(menuItem.getMenuItemId())
                .menuItemName(menuItem.getName())
                .build());

        log.info("Yeni değerlendirme bildirimi oluşturuldu. Ürün: {}, sahip ID: {}",
                menuItem.getMenuItemId(), owner.getUserId());
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(User user) {
        return notificationRepository
                .findByRecipientUserIdOrderByCreatedAtDesc(user.getUserId())
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(User user) {
        return notificationRepository.countByRecipientUserIdAndReadFalse(user.getUserId());
    }

    @Transactional
    public void markRead(Long id, User user) {
        AppNotification n = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bildirim bulunamadı. ID: " + id));
        if (!n.getRecipient().getUserId().equals(user.getUserId())) {
            throw new AccessDeniedException("Bu bildirim üzerinde yetkiniz yok.");
        }
        n.setRead(true);
        notificationRepository.save(n);
    }

    @Transactional
    public void markAllRead(User user) {
        List<AppNotification> unread =
                notificationRepository.findByRecipientUserIdAndReadFalse(user.getUserId());
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }
}
