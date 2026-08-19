package com.foodboxd.api.services;

import com.foodboxd.api.dtos.responses.RestaurantApplicationResponse;
import com.foodboxd.api.dtos.responses.UserResponse;
import com.foodboxd.api.entities.*;
import com.foodboxd.api.entities.RestaurantOwnershipStatus;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final RestaurantApplicationRepository applicationRepository;
    private final RestaurantOwnerRepository restaurantOwnerRepository;
    private final RestaurantRepository restaurantRepository;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    // ── Başvuru Listele ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RestaurantApplicationResponse> getAllApplications() {
        return applicationRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(RestaurantApplicationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RestaurantApplicationResponse> getPendingApplications() {
        return applicationRepository.findByStatusOrderByCreatedAtDesc(ApplicationStatus.PENDING)
                .stream()
                .map(RestaurantApplicationResponse::from)
                .toList();
    }

    // ── Başvuruyu Onayla ─────────────────────────────────────────────────────

    @Transactional
    public RestaurantApplicationResponse approveApplication(Long applicationId) {
        RestaurantApplication application = findApplication(applicationId);

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new IllegalStateException("Bu başvuru zaten işleme alınmış: " + application.getStatus());
        }

        Restaurant restaurant;

        if (application.getTargetRestaurant() != null) {
            // ── SAHİPLİK TALEBİ: mevcut restorana owner ata ─────────────────
            restaurant = application.getTargetRestaurant();

            // Restoranın zaten PRIMARY_OWNER'ı var mı?
            boolean alreadyHasOwner = restaurantOwnerRepository
                    .existsByRestaurantRestaurantIdAndRole(restaurant.getRestaurantId(), OwnerRole.PRIMARY_OWNER);
            if (alreadyHasOwner) {
                throw new IllegalStateException(
                        "Bu restoranın zaten bir sahibi var. ID: " + restaurant.getRestaurantId());
            }
            log.info("Sahiplik talebi onaylandı. Restoran ID: {}", restaurant.getRestaurantId());

        } else {
            // ── YENİ RESTORAN BAŞVURUSU: restoran oluştur ────────────────────
            Address address = addressRepository.save(
                    Address.builder()
                            .city(application.getCity())
                            .district(application.getDistrict())
                            .fullAddress(application.getFullAddress() != null
                                    ? application.getFullAddress()
                                    : application.getRestaurantName() + ", " + application.getCity())
                            .build()
            );
            restaurant = restaurantRepository.save(
                    Restaurant.builder()
                            .name(application.getRestaurantName())
                            .address(address)
                            .coOwnershipEnabled(false)
                            .build()
            );
            log.info("Yeni restoran başvurusu onaylandı. Restoran ID: {}", restaurant.getRestaurantId());
        }

        // Başvuranı PRIMARY_OWNER yap
        restaurantOwnerRepository.save(
                RestaurantOwner.builder()
                        .restaurant(restaurant)
                        .user(application.getApplicant())
                        .role(OwnerRole.PRIMARY_OWNER)
                        .build()
        );

        // Restoranın ownership_status'unu güncelle
        restaurant.setOwnershipStatus(RestaurantOwnershipStatus.OWNER_MANAGED);
        restaurantRepository.save(restaurant);

        // Kullanıcı rolünü RESTAURANT_OWNER yap
        User applicant = application.getApplicant();
        applicant.setRole(UserRole.RESTAURANT_OWNER);
        userRepository.save(applicant);

        // Başvuruyu güncelle
        application.setStatus(ApplicationStatus.APPROVED);
        application.setLinkedRestaurant(restaurant);
        application.setReviewedAt(Instant.now());
        applicationRepository.save(application);

        return RestaurantApplicationResponse.from(application);
    }

    // ── Başvuruyu Reddet ─────────────────────────────────────────────────────

    @Transactional
    public RestaurantApplicationResponse rejectApplication(Long applicationId, String adminNote) {
        RestaurantApplication application = findApplication(applicationId);

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new IllegalStateException("Bu başvuru zaten işleme alınmış: " + application.getStatus());
        }

        application.setStatus(ApplicationStatus.REJECTED);
        application.setAdminNote(adminNote);
        application.setReviewedAt(Instant.now());
        applicationRepository.save(application);

        log.info("Başvuru reddedildi. ID: {}", applicationId);
        return RestaurantApplicationResponse.from(application);
    }

    // ── Kullanıcı Yönetimi ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(userService::toResponse)
                .toList();
    }

    @Transactional
    public UserResponse changeUserRole(Long userId, UserRole newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı. ID: " + userId));
        user.setRole(newRole);
        userRepository.save(user);
        log.info("Kullanıcı rolü değiştirildi. ID: {}, Yeni rol: {}", userId, newRole);
        return userService.toResponse(user);
    }

    // ── Yardımcı ─────────────────────────────────────────────────────────────

    private RestaurantApplication findApplication(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Başvuru bulunamadı. ID: " + id));
    }
}
