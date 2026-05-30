package com.foodboxd.api.dtos.responses;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class RatingResponse {

    private Long ratingId;
    private Long userId;
    private String username;
    private Long menuItemId;
    private String menuItemName;
    private BigDecimal score;
    private String comment;
    private BigDecimal updatedAverageRating;
}
