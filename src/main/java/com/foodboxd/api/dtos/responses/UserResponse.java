package com.foodboxd.api.dtos.responses;

import com.foodboxd.api.entities.UserRole;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class UserResponse {

    private Long userId;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String profilePhotoUrl;
    private String bio;
    private UserRole role;

    // İsim/soyisim'in tekrar değiştirilebileceği en erken zaman.
    // null → şu an değiştirilebilir (15 günlük pencere dolmuş veya hiç değişmemiş).
    private LocalDateTime nameChangeAvailableAt;
}
