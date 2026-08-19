package com.foodboxd.api.services;

import com.foodboxd.api.dtos.responses.NotificationResponse;
import com.foodboxd.api.entities.AppNotification;
import com.foodboxd.api.entities.MenuItem;
import com.foodboxd.api.entities.RestaurantOwner;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.repositories.AppNotificationRepository;
import com.foodboxd.api.repositories.RestaurantOwnerRepository;
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
    private final RestaurantOwnerRepository restaurantOwnerRepository;

    /**
     * Bir ürüne değerlendirme yapıldığında restoranın sahiplerine bildirim oluşturur.
     * Değerlendiren kişinin adı maskeli gelir; owner kendi ürününü puanladıysa bildirim gitmez.
     * FCM push eklendiğinde bu noktadan push da tetiklenecek.
     */
    @Transactional
    public void notifyOwnersNewRating(MenuItem menuItem, Long raterUserId,
                                      String maskedRaterName, BigDecimal score) {
        List<RestaurantOwner> owners = restaurantOwnerRepository
                .findByRestaurantRestaurantId(menuItem.getRestaurant().getRestaurantId());

        for (RestaurantOwner owner : owners) {
            if (owner.getUser().getUserId().equals(raterUserId)) continue;

            notificationRepository.save(AppNotification.builder()
                    .recipient(owner.getUser())
                    .type("NEW_RATING")
                    .title("Yeni değerlendirme")
                    .body(maskedRaterName + ", \"" + menuItem.getName() + "\" için "
                            + score.stripTrailingZeros().toPlainString() + " puan verdi.")
                    .menuItemId(menuItem.getMenuItemId())
                    .menuItemName(menuItem.getName())
                    .build());
        }
        if (!owners.isEmpty()) {
            log.info("Yeni değerlendirme bildirimi oluşturuldu. Ürün: {}, sahip sayısı: {}",
                    menuItem.getMenuItemId(), owners.size());
        }
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
