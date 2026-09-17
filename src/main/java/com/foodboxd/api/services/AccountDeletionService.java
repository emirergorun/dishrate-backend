package com.foodboxd.api.services;

import com.foodboxd.api.entities.MenuItem;
import com.foodboxd.api.entities.Rating;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.repositories.AppNotificationRepository;
import com.foodboxd.api.repositories.RatingRepository;
import com.foodboxd.api.repositories.RefreshTokenRepository;
import com.foodboxd.api.repositories.RestaurantClaimRepository;
import com.foodboxd.api.repositories.RestaurantRepository;
import com.foodboxd.api.repositories.UserRepository;
import com.foodboxd.api.repositories.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kullanıcının kendi hesabını kalıcı olarak silmesi (App Store şartı).
 *
 * Silinenler: hesap, puanlar ve yorumlar (fotoğraflarıyla), istek listesi,
 * bildirimler, sahiplik talepleri, oturum anahtarları, profil fotoğrafı.
 * Kullanıcının sahibi olduğu restoranlar silinmez, sahipsiz kalır.
 * Puanları silinen yemeklerin ortalaması ve puan sayısı yeniden hesaplanır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final RatingRepository ratingRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final AppNotificationRepository notificationRepository;
    private final RestaurantClaimRepository claimRepository;
    private final RestaurantRepository restaurantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RatingService ratingService;
    private final FileStorageService fileStorageService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void deleteOwnAccount(User istekYapan, String password) {
        Long userId = istekYapan.getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found. ID: " + userId));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            // 401 yerine 409: istemcinin 401 → token yenileme akışını tetiklemesin
            throw new IllegalStateException("Şifre hatalı.");
        }

        List<Rating> ratings = ratingRepository.findByUser_UserId(userId);
        Map<Long, MenuItem> etkilenenYemekler = new LinkedHashMap<>();
        List<String> silinecekDosyalar = new ArrayList<>();
        for (Rating r : ratings) {
            etkilenenYemekler.putIfAbsent(r.getMenuItem().getMenuItemId(), r.getMenuItem());
            silinecekDosyalar.add(r.getPhotoUrl());
        }
        silinecekDosyalar.add(user.getProfilePhotoUrl());
        silinecekDosyalar.add(user.getProfilePhotoOriginalUrl());

        ratingRepository.deleteAllOfUser(userId);
        wishlistItemRepository.deleteAllOfUser(userId);
        notificationRepository.deleteAllOfUser(userId);
        claimRepository.deleteAllOfUser(userId);
        restaurantRepository.clearOwner(userId);
        refreshTokenRepository.deleteByUser(user);

        ratingService.recalculateAverages(etkilenenYemekler.values());

        userRepository.delete(user);

        // Dosyalar yalnızca veritabanı işlemi başarıyla bitince silinir;
        // işlem geri alınırsa fotoğraflar kaybolmasın.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                silinecekDosyalar.forEach(fileStorageService::deleteByUrl);
            }
        });

        log.info("Hesap silindi. ID: {}, puan: {}, etkilenen yemek: {}",
                userId, ratings.size(), etkilenenYemekler.size());
    }
}
