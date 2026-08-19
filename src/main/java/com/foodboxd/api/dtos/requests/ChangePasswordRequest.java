package com.foodboxd.api.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequest {

    @NotBlank(message = "Mevcut şifre boş bırakılamaz")
    private String currentPassword;

    @NotBlank(message = "Yeni şifre boş bırakılamaz")
    @Size(min = 6, max = 100, message = "Yeni şifre 6 ile 100 karakter arasında olmalıdır")
    private String newPassword;
}
