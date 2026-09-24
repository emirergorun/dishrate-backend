package com.foodboxd.api.dtos.requests;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateRatingRequest {

    @NotNull(message = "Kullanıcı ID boş bırakılamaz")
    private Long userId;

    @NotNull(message = "Yemek ID boş bırakılamaz")
    private Long menuItemId;

    @NotNull(message = "Puan boş bırakılamaz")
    @DecimalMin(value = "0.5", message = "Puan en az 0.5 olmalıdır")
    @DecimalMax(value = "5.0", message = "Puan en fazla 5.0 olabilir")
    private BigDecimal score;

    private String comment;

    /**
     * İsteğe bağlı fotoğraf. {@code null} → güncellemede mevcut fotoğraf
     * korunur (günlükten yalnızca puanı düzenlemek fotoğrafı silmesin);
     * boş metin → fotoğraf kaldırılır.
     */
    @Size(max = 500, message = "Fotoğraf adresi çok uzun")
    private String photoUrl;
}
