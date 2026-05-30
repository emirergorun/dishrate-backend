package com.foodboxd.api.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCategoryRequest {

    @NotBlank(message = "Kategori adı boş bırakılamaz")
    @Size(min = 2, max = 100, message = "Kategori adı 2 ile 100 karakter arasında olmalıdır")
    private String name;
}
