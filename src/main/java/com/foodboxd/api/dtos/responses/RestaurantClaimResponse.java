package com.foodboxd.api.dtos.responses;

import com.foodboxd.api.entities.ClaimStatus;
import com.foodboxd.api.entities.RestaurantClaim;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class RestaurantClaimResponse {

    private Long id;

    private Long userId;
    private String username;

    private Long restaurantId;
    private String restaurantName;

    /** Admin listesinde hangi restoran olduğu ayırt edilebilsin diye. */
    private String restaurantAddress;

    private ClaimStatus status;
    private Instant createdAt;
    private Instant reviewedAt;
    private String adminNote;

    public static RestaurantClaimResponse from(RestaurantClaim claim) {
        var restaurant = claim.getRestaurant();
        var address = restaurant.getAddress();
        return RestaurantClaimResponse.builder()
                .id(claim.getId())
                .userId(claim.getUser().getUserId())
                .username(claim.getUser().getUsername())
                .restaurantId(restaurant.getRestaurantId())
                .restaurantName(restaurant.getName())
                .restaurantAddress(address != null ? address.getFullAddress() : null)
                .status(claim.getStatus())
                .createdAt(claim.getCreatedAt())
                .reviewedAt(claim.getReviewedAt())
                .adminNote(claim.getAdminNote())
                .build();
    }
}
