package com.foodboxd.api.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "E-posta boş bırakılamaz")
    private String email;

    @NotBlank(message = "Şifre boş bırakılamaz")
    private String password;
}
