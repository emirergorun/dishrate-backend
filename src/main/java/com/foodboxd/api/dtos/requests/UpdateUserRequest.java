package com.foodboxd.api.dtos.requests;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRequest {

    @Size(min = 3, max = 50, message = "Kullanıcı adı 3 ile 50 karakter arasında olmalıdır")
    private String username;

    @Size(max = 50, message = "İsim en fazla 50 karakter olabilir")
    private String firstName;

    @Size(max = 50, message = "Soyisim en fazla 50 karakter olabilir")
    private String lastName;

    @Size(max = 500, message = "Biyografi en fazla 500 karakter olabilir")
    private String bio;

    private String profilePhotoUrl;
}
