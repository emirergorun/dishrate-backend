package com.foodboxd.api.services;

import com.foodboxd.api.dtos.requests.CreateRatingRequest;
import com.foodboxd.api.dtos.responses.MenuItemReviewResponse;
import com.foodboxd.api.dtos.responses.RatingResponse;
import com.foodboxd.api.entities.MenuItem;
import com.foodboxd.api.entities.Rating;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.exceptions.InvalidScoreException;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.repositories.MenuItemRepository;
import com.foodboxd.api.repositories.RatingRepository;
import com.foodboxd.api.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
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

    // -----------------------------------------------------------------------
    // UPSERT: Create or update a rating
    // -----------------------------------------------------------------------
    @Transactional
    public RatingResponse upsertRating(CreateRatingRequest request) {
        log.info("Rating UPSERT request. User ID: {}, Menu Item ID: {}, Score: {}",
                request.getUserId(), request.getMenuItemId(), request.getScore());

        validateScore(request.getScore());

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found. ID: " + request.getUserId()
                ));

        MenuItem menuItem = menuItemRepository.findById(request.getMenuItemId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Menu item not found. ID: " + request.getMenuItemId()
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
            savedRating = ratingRepository.save(rating);
            log.info("Rating updated. Rating ID: {}", savedRating.getRatingId());
        } else {
            log.info("No existing rating found. Creating new rating...");
            Rating newRating = Rating.builder()
                    .user(user)
                    .menuItem(menuItem)
                    .score(request.getScore())
                    .comment(request.getComment())
                    .build();
            savedRating = ratingRepository.save(newRating);
            log.info("New rating created. Rating ID: {}", savedRating.getRatingId());
        }

        BigDecimal updatedAverage = recalculateAverage(menuItem);
        log.info("Updated average for menu item (ID: {}): {}", menuItem.getMenuItemId(), updatedAverage);

        // Restoran sahiplerine bildirim (değerlendiren maskeli; owner kendini puanladıysa gitmez)
        notificationService.notifyOwnersNewRating(
                menuItem, user.getUserId(), maskName(user), savedRating.getScore());

        return toResponse(savedRating, updatedAverage);
    }

    // -----------------------------------------------------------------------
    // Get all ratings for a menu item
    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<MenuItemReviewResponse> getRatingsByMenuItem(Long menuItemId, User viewer) {
        log.debug("Fetching ratings for menu item ID: {}", menuItemId);
        if (!menuItemRepository.existsById(menuItemId)) {
            throw new ResourceNotFoundException("Menu item not found. ID: " + menuItemId);
        }
        Long viewerId = viewer != null ? viewer.getUserId() : null;
        return ratingRepository.findByMenuItem_MenuItemId(menuItemId)
                .stream()
                .map(r -> {
                    boolean mine = viewerId != null
                            && r.getUser().getUserId().equals(viewerId);
                    return MenuItemReviewResponse.builder()
                            .ratingId(r.getRatingId())
                            // Kendi yorumu gerçek ad, başkasınınki maskeli
                            .reviewerName(mine ? displayName(r.getUser()) : maskName(r.getUser()))
                            .mine(mine)
                            .score(r.getScore())
                            .comment(r.getComment())
                            .ratedAt(r.getUpdatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    // İsim/soyisim varsa "Ad Soyad", yoksa kullanıcı adı.
    private String displayName(User u) {
        String first = u.getFirstName();
        String last = u.getLastName();
        if (first != null && !first.isBlank()) {
            return last != null && !last.isBlank()
                    ? first.trim() + " " + last.trim()
                    : first.trim();
        }
        return u.getUsername();
    }

    // "Emir Ergörün" → "E*** E***", "emir_test" → "e***"
    private String maskName(User u) {
        String first = u.getFirstName();
        String last = u.getLastName();
        if (first != null && !first.isBlank()) {
            String masked = maskWord(first);
            if (last != null && !last.isBlank()) masked += " " + maskWord(last);
            return masked;
        }
        return maskWord(u.getUsername());
    }

    private String maskWord(String s) {
        String t = s == null ? "" : s.trim();
        return t.isEmpty() ? "***" : t.charAt(0) + "***";
    }

    // -----------------------------------------------------------------------
    // Get all ratings by a user
    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<RatingResponse> getRatingsByUser(Long userId) {
        log.debug("Fetching ratings for user ID: {}", userId);
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found. ID: " + userId);
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
    public void deleteRating(Long ratingId) {
        log.info("Delete rating request. ID: {}", ratingId);
        Rating rating = ratingRepository.findById(ratingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rating not found. ID: " + ratingId
                ));

        MenuItem menuItem = rating.getMenuItem();
        ratingRepository.deleteById(ratingId);
        log.info("Rating deleted. ID: {}", ratingId);

        recalculateAverage(menuItem);
        log.info("Average recalculated for menu item (ID: {}) after deletion.", menuItem.getMenuItemId());
    }

    // -----------------------------------------------------------------------
    // Private: Recalculate and persist average rating
    // -----------------------------------------------------------------------
    private BigDecimal recalculateAverage(MenuItem menuItem) {
        BigDecimal average = ratingRepository
                .calculateAverageScoreByMenuItemId(menuItem.getMenuItemId())
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        menuItem.setAverageRating(average);
        menuItemRepository.save(menuItem);
        return average;
    }

    // -----------------------------------------------------------------------
    // Private: Validate score range
    // -----------------------------------------------------------------------
    private void validateScore(BigDecimal score) {
        if (score == null) {
            throw new InvalidScoreException("Score cannot be null.");
        }
        if (score.compareTo(MIN_SCORE) < 0 || score.compareTo(MAX_SCORE) > 0) {
            throw new InvalidScoreException(
                    "Score must be between 0.5 and 5.0. Provided: " + score
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
