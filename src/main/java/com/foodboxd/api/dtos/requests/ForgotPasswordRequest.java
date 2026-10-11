package com.foodboxd.api.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Şifremi unuttum, 1. adım: e-posta ya da kullanıcı adı (2.7). */
@Getter
@Setter
public class ForgotPasswordRequest {

    @NotBlank(message = "E-posta ya da kullanıcı adı gerekli")
    @Size(max = 255)
    private String identifier;
}
