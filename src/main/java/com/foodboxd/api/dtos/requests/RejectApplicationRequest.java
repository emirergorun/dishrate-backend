package com.foodboxd.api.dtos.requests;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectApplicationRequest {
    private String adminNote; // Opsiyonel red sebebi
}
