package com.foodboxd.api.dtos.responses;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CategoryResponse {

    private Long categoryId;
    private String name;
    /** Değişmeyen anahtar; istemci süzgeci ve ikonu buna bağlar (2.10b). */
    private String slug;
    private Integer sortOrder;
}
