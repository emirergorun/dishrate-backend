package com.foodboxd.api.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** E-posta doğrulama kodu (2.8). */
@Getter
@Setter
public class VerifyEmailRequest {

    @NotBlank(message = "Kod gerekli")
    @Pattern(regexp = "\\d{6}", message = "Kod 6 haneli olmalı")
    private String code;
}
