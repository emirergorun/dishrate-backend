package com.foodboxd.api.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRestaurantRequest {

    @NotNull(message = "Adres ID boş bırakılamaz")
    private Long addressId;

    @NotBlank(message = "Restoran adı boş bırakılamaz")
    @Size(min = 2, max = 255, message = "Restoran adı 2 ile 255 karakter arasında olmalıdır")
    private String name;

    private String logoUrl;
}
