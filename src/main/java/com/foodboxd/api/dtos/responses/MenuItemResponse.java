package com.foodboxd.api.dtos.responses;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class MenuItemResponse {

    private Long menuItemId;
    private String name;
    private BigDecimal price;
    private BigDecimal averageRating;
    private String photoUrl;
    private Long restaurantId;
    private String restaurantName;
    private Double restaurantLatitude;
    private Double restaurantLongitude;
    private CategoryResponse category;
}
