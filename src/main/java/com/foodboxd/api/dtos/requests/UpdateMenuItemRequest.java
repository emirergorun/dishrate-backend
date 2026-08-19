package com.foodboxd.api.dtos.requests;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Menü öğesi güncelleme — yalnızca verilen alanlar değiştirilir. */
@Getter
@Setter
public class UpdateMenuItemRequest {

    @Size(max = 255, message = "Ürün adı en fazla 255 karakter olabilir")
    private String name;

    @DecimalMin(value = "0.0", message = "Fiyat 0'dan küçük olamaz")
    private BigDecimal price;

    private String photoUrl;

    private Long categoryId;
}
