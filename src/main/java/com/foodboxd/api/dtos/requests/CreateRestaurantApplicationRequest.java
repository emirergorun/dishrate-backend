package com.foodboxd.api.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRestaurantApplicationRequest {

    @NotBlank(message = "Restoran adı gerekli")
    @Size(max = 255)
    private String restaurantName;

    @NotBlank(message = "Şehir gerekli")
    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String district;

    private String fullAddress;

    @Size(max = 20)
    private String contactPhone;

    private String description;
}
