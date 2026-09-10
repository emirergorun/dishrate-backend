package com.foodboxd.api.services;

import com.foodboxd.api.dtos.responses.RestaurantClaimResponse;
import com.foodboxd.api.entities.ClaimStatus;
import com.foodboxd.api.entities.Restaurant;
import com.foodboxd.api.entities.RestaurantClaim;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.entities.UserRole;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.repositories.RestaurantClaimRepository;
import com.foodboxd.api.repositories.RestaurantRepository;
import com.foodboxd.api.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Restoran sahipliği talepleri.
 *
 * <p>Model: katalogdaki her restoran sahipsiz doğar. Bir kullanıcı "burası
 * benim" der ({@link #claim}), admin elle onaylar ({@link #review}). Onayla
 * birlikte {@code restaurants.owner_id} atanır ve kullanıcının rolü
 * RESTAURANT_OWNER'a yükselir — hesap değişmez, üzerine yetki eklenir.
 *
 * <p>Şu an otomatik doğrulama yok; karar adminin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimService {

    private final RestaurantClaimRepository claimRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;

    // ── Kullanıcı tarafı ──────────────────────────────────────────────────────

    /**
     * Bir restoran için sahiplik talebi açar.
     *
     * <p>Reddedilmiş bir talep varsa üzerine yazılır: kullanıcı eksiğini
     * tamamlayıp tekrar deneyebilmeli. Bekleyen ya da onaylanmış talep varsa
     * yeni talep açılmaz.
     */
    @Transactional
    public RestaurantClaimResponse claim(User user, Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restoran bulunamadı. ID: " + restaurantId));

        if (!restaurant.isClaimable()) {
            throw new IllegalStateException(
                    "Bu restoranın zaten bir sahibi var: " + restaurant.getName());
        }

        // Aynı restoran için başkasının bekleyen talebi varsa sıraya girilmez;
        // admin önce onu sonuçlandırsın, yoksa iki talep birden onaylanabilir.
        boolean baskasiBekliyor = claimRepository
                .existsByRestaurantRestaurantIdAndStatus(restaurantId, ClaimStatus.PENDING);

        RestaurantClaim existing = claimRepository
                .findByUserUserIdAndRestaurantRestaurantId(user.getUserId(), restaurantId)
                .orElse(null);

        if (existing != null) {
            if (existing.getStatus() == ClaimStatus.PENDING) {
                throw new IllegalStateException(
                        "Bu restoran için zaten bekleyen bir talebin var.");
            }
            if (existing.getStatus() == ClaimStatus.APPROVED) {
                throw new IllegalStateException("Bu restoranın sahibi zaten sensin.");
            }
            if (baskasiBekliyor) {
                throw new IllegalStateException(
                        "Bu restoran için inceleme bekleyen başka bir talep var.");
            }
            // Reddedilmişti — aynı kaydı yeniden aç.
            existing.setStatus(ClaimStatus.PENDING);
            existing.setCreatedAt(Instant.now());
            existing.setReviewedAt(null);
            existing.setAdminNote(null);
            RestaurantClaim saved = claimRepository.save(existing);
            log.info("Sahiplik talebi yeniden açıldı. Talep ID: {}, Kullanıcı ID: {}",
                    saved.getId(), user.getUserId());
            return RestaurantClaimResponse.from(saved);
        }

        if (baskasiBekliyor) {
            throw new IllegalStateException(
                    "Bu restoran için inceleme bekleyen başka bir talep var.");
        }

        RestaurantClaim saved = claimRepository.save(
                RestaurantClaim.builder()
                        .user(user)
                        .restaurant(restaurant)
                        .status(ClaimStatus.PENDING)
                        .build());

        log.info("Sahiplik talebi alındı. Talep ID: {}, Kullanıcı ID: {}, Restoran ID: {}",
                saved.getId(), user.getUserId(), restaurantId);
        return RestaurantClaimResponse.from(saved);
    }

    /** Kullanıcının kendi talepleri — en yeni önce. */
    @Transactional(readOnly = true)
    public List<RestaurantClaimResponse> myClaims(User user) {
        return claimRepository.findByUserUserIdOrderByCreatedAtDesc(user.getUserId())
                .stream()
                .map(RestaurantClaimResponse::from)
                .toList();
    }

    // ── Admin tarafı ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RestaurantClaimResponse> list(boolean pendingOnly) {
        var claims = pendingOnly
                ? claimRepository.findByStatusOrderByCreatedAtDesc(ClaimStatus.PENDING)
                : claimRepository.findAllByOrderByCreatedAtDesc();
        return claims.stream().map(RestaurantClaimResponse::from).toList();
    }

    /**
     * Talebi sonuçlandırır.
     *
     * <p>Onayda üç şey birlikte olur ve hepsi aynı işlemde: restoranın sahibi
     * atanır, kullanıcının rolü yükseltilir, talep APPROVED'a geçer. Biri
     * başarısız olursa hiçbiri yazılmaz.
     */
    @Transactional
    public RestaurantClaimResponse review(Long claimId, ClaimStatus decision, String adminNote) {
        if (decision != ClaimStatus.APPROVED && decision != ClaimStatus.REJECTED) {
            throw new IllegalArgumentException(
                    "Karar yalnızca APPROVED veya REJECTED olabilir.");
        }

        RestaurantClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Talep bulunamadı. ID: " + claimId));

        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw new IllegalStateException(
                    "Bu talep zaten sonuçlandırılmış: " + claim.getStatus());
        }

        if (decision == ClaimStatus.APPROVED) {
            Restaurant restaurant = claim.getRestaurant();

            // Talep açıldıktan sonra restoran başkasına verilmiş olabilir.
            if (!restaurant.isClaimable()) {
                throw new IllegalStateException(
                        "Bu restoranın sahibi bu talep beklerken atanmış. "
                                + "Restoran ID: " + restaurant.getRestaurantId());
            }

            User user = claim.getUser();
            restaurant.setOwner(user);
            restaurantRepository.save(restaurant);

            // Hesap korunur, rol yükselir. Admin'i owner'a düşürmeyiz.
            if (!user.getRole().atLeast(UserRole.RESTAURANT_OWNER)) {
                user.setRole(UserRole.RESTAURANT_OWNER);
                userRepository.save(user);
            }

            log.info("Sahiplik talebi onaylandı. Talep ID: {}, Restoran ID: {}, Yeni sahip ID: {}",
                    claimId, restaurant.getRestaurantId(), user.getUserId());
        } else {
            log.info("Sahiplik talebi reddedildi. Talep ID: {}", claimId);
        }

        claim.setStatus(decision);
        claim.setAdminNote(adminNote);
        claim.setReviewedAt(Instant.now());
        return RestaurantClaimResponse.from(claimRepository.save(claim));
    }
}
