package com.foodboxd.api.services;

import com.foodboxd.api.security.Ownership;
import com.foodboxd.api.dtos.requests.CreateRatingRequest;
import com.foodboxd.api.dtos.responses.MenuItemReviewResponse;
import com.foodboxd.api.dtos.responses.RatingResponse;
import com.foodboxd.api.entities.MenuItem;
import com.foodboxd.api.entities.Rating;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.exceptions.InvalidScoreException;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.repositories.MenuItemRepository;
import com.foodboxd.api.repositories.RatingReportRepository;
import com.foodboxd.api.repositories.RatingRepository;
import com.foodboxd.api.repositories.UserBlockRepository;
import com.foodboxd.api.repositories.UserRepository;
import com.foodboxd.api.utils.NameMask;
import com.foodboxd.api.utils.ProfanityFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RatingService {

    private static final BigDecimal MIN_SCORE = new BigDecimal("0.5");
    private static final BigDecimal MAX_SCORE = new BigDecimal("5.0");

    private final RatingRepository ratingRepository;
    private final UserRepository userRepository;
    private final MenuItemRepository menuItemRepository;
    private final NotificationService notificationService;
    private final RatingReportRepository reportRepository;
    private final UserBlockRepository blockRepository;
    private final ProfanityFilter profanityFilter;

    // -----------------------------------------------------------------------
    // UPSERT: Create or update a rating
    // -----------------------------------------------------------------------
    @Transactional
    public RatingResponse upsertRating(CreateRatingRequest request) {
        log.info("Rating UPSERT request. User ID: {}, Menu Item ID: {}, Score: {}",
                request.getUserId(), request.getMenuItemId(), request.getScore());

        validateScore(request.getScore());
        String photoUrl = validatePhotoUrl(request.getPhotoUrl());
        if (profanityFilter.containsProfanity(request.getComment())) {
            throw new IllegalArgumentException(
                    "Yorumunda uygunsuz bir ifade var, düzenleyip tekrar dene.");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Kullanıcı bulunamadı."
                ));

        MenuItem menuItem = menuItemRepository.findById(request.getMenuItemId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Yemek bulunamadı."
                ));

        Optional<Rating> existingRating = ratingRepository
                .findByUser_UserIdAndMenuItem_MenuItemId(
                        request.getUserId(), request.getMenuItemId()
                );

        Rating savedRating;

        if (existingRating.isPresent()) {
            Rating rating = existingRating.get();
            log.info("Existing rating found (ID: {}). Updating...", rating.getRatingId());
            rating.setScore(request.getScore());
            rating.setComment(request.getComment());
            // null → dokunma; boş metin → kaldır (bkz. CreateRatingRequest)
            if (photoUrl != null) {
                rating.setPhotoUrl(photoUrl.isEmpty() ? null : photoUrl);
            }
            savedRating = ratingRepository.save(rating);
            log.info("Rating updated. Rating ID: {}", savedRating.getRatingId());
        } else {
            log.info("No existing rating found. Creating new rating...");
            Rating newRating = Rating.builder()
                    .user(user)
                    .menuItem(menuItem)
                    .score(request.getScore())
                    .comment(request.getComment())
                    .photoUrl(photoUrl == null || photoUrl.isEmpty() ? null : photoUrl)
                    .build();
            savedRating = ratingRepository.save(newRating);
            log.info("New rating created. Rating ID: {}", savedRating.getRatingId());
        }

        BigDecimal updatedAverage = recalculateAverage(menuItem);
        log.info("Updated average for menu item (ID: {}): {}", menuItem.getMenuItemId(), updatedAverage);

        // Restoran sahiplerine bildirim (değerlendiren maskeli; owner kendini puanladıysa gitmez)
        notificationService.notifyOwnersNewRating(
                menuItem, user.getUserId(), NameMask.of(user), savedRating.getScore());

        return toResponse(savedRating, updatedAverage);
    }

    // -----------------------------------------------------------------------
    // Get all ratings for a menu item
    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<MenuItemReviewResponse> getRatingsByMenuItem(Long menuItemId, User viewer) {
        log.debug("Fetching ratings for menu item ID: {}", menuItemId);
        if (!menuItemRepository.existsById(menuItemId)) {
            throw new ResourceNotFoundException("Yemek bulunamadı.");
        }
        Long viewerId = viewer != null ? viewer.getUserId() : null;
        // Gösterilmeyenler (1.7): bildirim eşiğini aşıp gizlenenler, izleyenin
        // bildirdikleri ve izleyenle arasında engel olan kullanıcılarınkiler.
        // Kendi değerlendirmesi her zaman görünür.
        Set<Long> reported = viewerId != null
                ? reportRepository.findRatingIdsReportedBy(viewerId) : Set.of();
        Set<Long> blockedUsers = viewerId != null
                ? blockRepository.findRelatedUserIds(viewerId) : Set.of();
        return ratingRepository.findByMenuItem_MenuItemId(menuItemId)
                .stream()
                .filter(r -> {
                    Long authorId = r.getUser().getUserId();
                    if (authorId.equals(viewerId)) return true;
                    return !r.isHidden()
                            && !reported.contains(r.getRatingId())
                            && !blockedUsers.contains(authorId);
                })
                .map(r -> {
                    boolean mine = viewerId != null
                            && r.getUser().getUserId().equals(viewerId);
                    return MenuItemReviewResponse.builder()
                            .ratingId(r.getRatingId())
                            // Herkes kullanıcı adıyla görünür (karar 25 Eylül):
                            // maskeli ad ("A*** B***") kişileri ayırt
                            // ettirmiyordu. Ad ve soyad gösterilmez.
                            .reviewerName("@" + r.getUser().getUsername())
                            .mine(mine)
                            .score(r.getScore())
                            .comment(r.getComment())
                            .photoUrl(r.getPhotoUrl())
                            .ratedAt(r.getUpdatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Get all ratings by a user
    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<RatingResponse> getRatingsByUser(Long userId) {
        log.debug("Fetching ratings for user ID: {}", userId);
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Kullanıcı bulunamadı.");
        }
        return ratingRepository.findByUser_UserId(userId)
                .stream()
                .map(r -> toResponse(r, null))
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Delete a rating
    // -----------------------------------------------------------------------
    @Transactional
    public void deleteRating(Long ratingId, User currentUser) {
        log.info("Delete rating request. ID: {}", ratingId);
        Rating rating = ratingRepository.findById(ratingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Değerlendirme bulunamadı."
                ));
        Ownership.requireSelf(currentUser, rating.getUser().getUserId());

        MenuItem menuItem = rating.getMenuItem();
        ratingRepository.deleteById(ratingId);
        log.info("Rating deleted. ID: {}", ratingId);

        recalculateAverage(menuItem);
        log.info("Average recalculated for menu item (ID: {}) after deletion.", menuItem.getMenuItemId());
    }

    // -----------------------------------------------------------------------
    // Private: Recalculate and persist average rating
    // -----------------------------------------------------------------------
    /** Hesap silme gibi toplu işlemlerden sonra ortalama ve sayıyı tazeler. */
    @Transactional
    public void recalculateAverages(java.util.Collection<MenuItem> menuItems) {
        menuItems.forEach(this::recalculateAverage);
    }

    private BigDecimal recalculateAverage(MenuItem menuItem) {
        BigDecimal average = ratingRepository
                .calculateAverageScoreByMenuItemId(menuItem.getMenuItemId())
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        menuItem.setAverageRating(average);
        menuItem.setRatingCount((int) ratingRepository.countByMenuItem_MenuItemId(menuItem.getMenuItemId()));
        menuItemRepository.save(menuItem);
        return average;
    }

    /**
     * Yalnızca bizim `/files` uç noktamızın döndürdüğü adresler kabul edilir.
     * Serbest bir adres, yorumu okuyan herkesin telefonuna üçüncü taraf bir
     * sunucudan görsel indirtmek (ve onları izlemek) için kullanılabilirdi.
     */
    private String validatePhotoUrl(String url) {
        if (url == null) return null;
        String t = url.trim();
        if (t.isEmpty()) return "";
        if (!UPLOADED_FILE.matcher(t).matches()) {
            throw new IllegalArgumentException("Geçersiz fotoğraf adresi.");
        }
        return t;
    }

    private static final java.util.regex.Pattern UPLOADED_FILE =
            java.util.regex.Pattern.compile("^https?://[^/\\s]+/api/v1/files/[A-Za-z0-9._-]+$");

    // -----------------------------------------------------------------------
    // Private: Validate score range
    // -----------------------------------------------------------------------
    private void validateScore(BigDecimal score) {
        if (score == null) {
            throw new InvalidScoreException("Puan boş olamaz.");
        }
        if (score.compareTo(MIN_SCORE) < 0 || score.compareTo(MAX_SCORE) > 0) {
            throw new InvalidScoreException(
                    "Puan 0.5 ile 5.0 arasında olmalı."
            );
        }
    }

    // -----------------------------------------------------------------------
    // Private: Entity → Response DTO
    // -----------------------------------------------------------------------
    private RatingResponse toResponse(Rating rating, BigDecimal updatedAverage) {
        return RatingResponse.builder()
                .ratingId(rating.getRatingId())
                .userId(rating.getUser().getUserId())
                .username(rating.getUser().getUsername())
                .menuItemId(rating.getMenuItem().getMenuItemId())
                .menuItemName(rating.getMenuItem().getName())
                .photoUrl(rating.getMenuItem().getPhotoUrl())
                .reviewPhotoUrl(rating.getPhotoUrl())
                .restaurantId(rating.getMenuItem().getRestaurant().getRestaurantId())
                .restaurantName(rating.getMenuItem().getRestaurant().getName())
                .categoryName(rating.getMenuItem().getCategory() != null
                        ? rating.getMenuItem().getCategory().getName()
                        : null)
                .score(rating.getScore())
                .comment(rating.getComment())
                .ratedAt(rating.getUpdatedAt())
                .updatedAverageRating(updatedAverage)
                .build();
    }
}
