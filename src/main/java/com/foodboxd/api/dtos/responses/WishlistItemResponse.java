package com.foodboxd.api.dtos.responses;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class WishlistItemResponse {

    private Long wishId;
    private Long userId;
    private MenuItemResponse menuItem;
}
