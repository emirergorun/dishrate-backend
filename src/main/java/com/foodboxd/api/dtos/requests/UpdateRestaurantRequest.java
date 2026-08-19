package com.foodboxd.api.dtos.requests;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Restoran bilgisi güncelleme — yalnızca verilen alanlar değiştirilir. */
@Getter
@Setter
public class UpdateRestaurantRequest {

    @Size(max = 255, message = "Restoran adı en fazla 255 karakter olabilir")
    private String name;

    private String logoUrl;

    @Size(max = 100, message = "Şehir en fazla 100 karakter olabilir")
    private String city;

    @Size(max = 100, message = "İlçe en fazla 100 karakter olabilir")
    private String district;

    private String fullAddress;

    private Double latitude;

    private Double longitude;
}
