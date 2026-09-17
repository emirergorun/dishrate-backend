package com.foodboxd.api.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeleteAccountRequest {

    /** Hesabı silmeden önce mevcut şifre yeniden doğrulanır. */
    @NotBlank(message = "Şifre boş bırakılamaz")
    private String password;
}
