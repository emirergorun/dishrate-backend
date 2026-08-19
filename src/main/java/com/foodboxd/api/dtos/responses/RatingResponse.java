package com.foodboxd.api.dtos.responses;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class RatingResponse {

    private Long ratingId;
    private Long userId;
    private String username;
    private Long menuItemId;
    private String menuItemName;
    private String photoUrl;
    private String restaurantName;
    private String categoryName;
    private BigDecimal score;
    private String comment;
    private LocalDateTime ratedAt;
    private BigDecimal updatedAverageRating;
}
