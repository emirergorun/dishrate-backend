package com.foodboxd.api.dtos.responses;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AddressResponse {

    private Long addressId;
    private String city;
    private String district;
    private String fullAddress;
    private Double latitude;
    private Double longitude;
}
