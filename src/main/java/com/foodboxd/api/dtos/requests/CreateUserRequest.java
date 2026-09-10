package com.foodboxd.api.dtos.requests;

import com.foodboxd.api.validation.StrongPassword;
import com.foodboxd.api.validation.ValidEmail;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserRequest {

    @NotBlank(message = "Kullanıcı adı boş bırakılamaz")
    @Size(min = 3, max = 50, message = "Kullanıcı adı 3 ile 50 karakter arasında olmalıdır")
    @Pattern(
            regexp = "^\\s*[A-Za-z0-9._-]+\\s*$",
            message = "Kullanıcı adı boşluk içeremez; yalnızca harf, rakam, nokta, alt çizgi ve tire kullanılabilir"
    )
    private String username;

    @NotBlank(message = "İsim boş bırakılamaz")
    @Size(max = 50, message = "İsim en fazla 50 karakter olabilir")
    private String firstName;

    @NotBlank(message = "Soyisim boş bırakılamaz")
    @Size(max = 50, message = "Soyisim en fazla 50 karakter olabilir")
    private String lastName;

    @NotBlank(message = "E-posta adresi boş bırakılamaz")
    @ValidEmail
    @Size(max = 150, message = "E-posta adresi en fazla 150 karakter olabilir")
    private String email;

    @NotBlank(message = "Şifre boş bırakılamaz")
    @Size(max = 100, message = "Şifre en fazla 100 karakter olabilir")
    @StrongPassword
    private String password;

    private String profilePhotoUrl;

    @Size(max = 500, message = "Biyografi en fazla 500 karakter olabilir")
    private String bio;
}
