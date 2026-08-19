package com.foodboxd.api.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token boş bırakılamaz")
    private String refreshToken;
}
