package com.foodboxd.api.dtos.responses;

import com.foodboxd.api.entities.ApplicationStatus;
import com.foodboxd.api.entities.RestaurantApplication;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class RestaurantApplicationResponse {

    private Long id;
    private Long applicantId;
    private String applicantUsername;
    private String applicantEmail;
    private String restaurantName;
    private String city;
    private String district;
    private String fullAddress;
    private String contactPhone;
    private String description;
    private ApplicationStatus status;
    private String type;              // "NEW_RESTAURANT" veya "CLAIM"
    private Long targetRestaurantId;  // Sahiplik talebi ise hedef restoran
    private String adminNote;
    private Long linkedRestaurantId;
    private Instant createdAt;
    private Instant reviewedAt;

    public static RestaurantApplicationResponse from(RestaurantApplication app) {
        boolean isClaim = app.getTargetRestaurant() != null;
        return RestaurantApplicationResponse.builder()
                .id(app.getId())
                .applicantId(app.getApplicant().getUserId())
                .applicantUsername(app.getApplicant().getUsername())
                .applicantEmail(app.getApplicant().getEmail())
                .restaurantName(isClaim
                        ? app.getTargetRestaurant().getName()
                        : app.getRestaurantName())
                .city(app.getCity())
                .district(app.getDistrict())
                .fullAddress(app.getFullAddress())
                .contactPhone(app.getContactPhone())
                .description(app.getDescription())
                .status(app.getStatus())
                .type(isClaim ? "CLAIM" : "NEW_RESTAURANT")
                .targetRestaurantId(isClaim
                        ? app.getTargetRestaurant().getRestaurantId() : null)
                .adminNote(app.getAdminNote())
                .linkedRestaurantId(app.getLinkedRestaurant() != null
                        ? app.getLinkedRestaurant().getRestaurantId() : null)
                .createdAt(app.getCreatedAt())
                .reviewedAt(app.getReviewedAt())
                .build();
    }
}
