package com.foodboxd.api.dtos.responses;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RestaurantResponse {

    private Long restaurantId;
    private String name;
    private String logoUrl;
    private AddressResponse address;
}
