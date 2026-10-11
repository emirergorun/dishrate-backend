package com.foodboxd.api.dtos.requests;

import com.foodboxd.api.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Şifremi unuttum, 2. adım: kod + yeni şifre (2.7). */
@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank(message = "E-posta ya da kullanıcı adı gerekli")
    @Size(max = 255)
    private String identifier;

    @NotBlank(message = "Kod gerekli")
    @Pattern(regexp = "\\d{6}", message = "Kod 6 haneli olmalı")
    private String code;

    @NotBlank(message = "Yeni şifre boş bırakılamaz")
    @Size(max = 100, message = "Yeni şifre en fazla 100 karakter olabilir")
    @StrongPassword
    private String newPassword;
}
