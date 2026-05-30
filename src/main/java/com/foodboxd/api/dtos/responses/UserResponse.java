package com.foodboxd.api.dtos.responses;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserResponse {

    private Long userId;
    private String username;
    private String email;
    private String profilePhotoUrl;
    private String bio;
}
