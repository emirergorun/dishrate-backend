package com.foodboxd.api.dtos.responses;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private long expiresIn;  // saniye cinsinden (3600)
    private UserResponse user;
}
