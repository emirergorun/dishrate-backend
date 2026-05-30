package com.foodboxd.api.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAddressRequest {

    @NotBlank(message = "Şehir boş bırakılamaz")
    private String city;

    private String district;

    @NotBlank(message = "Tam adres boş bırakılamaz")
    private String fullAddress;

    private Double latitude;

    private Double longitude;
}
