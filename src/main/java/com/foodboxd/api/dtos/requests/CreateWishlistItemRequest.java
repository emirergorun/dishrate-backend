package com.foodboxd.api.dtos.requests;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateWishlistItemRequest {

    @NotNull(message = "Kullanıcı ID boş bırakılamaz")
    private Long userId;

    @NotNull(message = "Menü ürünü ID boş bırakılamaz")
    private Long menuItemId;
}
