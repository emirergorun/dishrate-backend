package com.foodboxd.api.dtos.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserRequest {

    @NotBlank(message = "Kullanıcı adı boş bırakılamaz")
    @Size(min = 3, max = 50, message = "Kullanıcı adı 3 ile 50 karakter arasında olmalıdır")
    private String username;

    @NotBlank(message = "E-posta adresi boş bırakılamaz")
    @Email(message = "Geçerli bir e-posta adresi giriniz")
    @Size(max = 150, message = "E-posta adresi en fazla 150 karakter olabilir")
    private String email;

    @NotBlank(message = "Şifre boş bırakılamaz")
    @Size(min = 6, max = 100, message = "Şifre 6 ile 100 karakter arasında olmalıdır")
    private String password;

    private String profilePhotoUrl;

    @Size(max = 500, message = "Biyografi en fazla 500 karakter olabilir")
    private String bio;
}
