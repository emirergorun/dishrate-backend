package com.foodboxd.api.dtos.requests;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class CreateMenuItemRequest {

    @NotNull(message = "Restoran ID boş bırakılamaz")
    private Long restaurantId;

    private Long categoryId;

    @NotBlank(message = "Ürün adı boş bırakılamaz")
    private String name;

    private String photoUrl;
}
