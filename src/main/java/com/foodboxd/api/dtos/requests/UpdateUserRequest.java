package com.foodboxd.api.dtos.requests;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRequest {

    @Size(max = 500, message = "Biyografi en fazla 500 karakter olabilir")
    private String bio;

    private String profilePhotoUrl;
}
