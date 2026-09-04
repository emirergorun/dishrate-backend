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

    @NotBlank(message = "İlçe gerekli")
    @Size(max = 100)
    private String district;

    // ── Açık adres bileşenleri ────────────────────────────────────────────────
    @NotBlank(message = "Adres satırı 1 gerekli (mahalle, cadde/sokak)")
    @Size(max = 255)
    private String addressLine1;

    @Size(max = 255)
    private String addressLine2;

    @NotBlank(message = "Bina no gerekli")
    @Size(max = 20)
    private String buildingNo;

    @Size(max = 30)
    private String floorApartment;

    @Size(max = 10)
    private String postalCode;

    /// Parçalardan otomatik üretilir; istemci göndermek zorunda değil.
    private String fullAddress;

    @Size(max = 20)
    private String contactPhone;

    private String description;
}
