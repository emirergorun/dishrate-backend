package com.foodboxd.api.dtos.requests;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateMenuItemRequest {

    @NotNull(message = "Restoran ID boş bırakılamaz")
    private Long restaurantId;

    private Long categoryId;

    @NotBlank(message = "Ürün adı boş bırakılamaz")
    private String name;

    @DecimalMin(value = "0.0", message = "Fiyat 0'dan küçük olamaz")
    private BigDecimal price;

    private String photoUrl;
}
