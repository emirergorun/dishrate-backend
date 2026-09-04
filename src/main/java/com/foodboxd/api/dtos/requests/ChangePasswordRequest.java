package com.foodboxd.api.dtos.requests;

import com.foodboxd.api.validation.StrongPassword;
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
    @Size(max = 100, message = "Yeni şifre en fazla 100 karakter olabilir")
    @StrongPassword
    private String newPassword;
}
