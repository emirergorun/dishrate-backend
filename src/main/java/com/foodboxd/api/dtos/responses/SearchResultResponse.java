package com.foodboxd.api.dtos.responses;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Arama ekranında bir restoran kartı: restoran + aramayla eşleşen yemekleri.
 *
 * <p>Restoranın adı eşleştiyse {@code items} restoranın menüsüdür (adında
 * aranan kelime geçmese de); yoksa yalnızca adı/kategorisi eşleşen yemekler.
 * Menüsü boş bir restoran da adı eşleşiyorsa listelenir.
 */
@Getter
@Builder
public class SearchResultResponse {

    private Long restaurantId;
    private String name;
    private String city;
    private String district;
    private Double latitude;
    private Double longitude;

    /** Restoranın kendi adı aramayla eşleşti mi — kartlar önce bunlar. */
    private boolean nameMatched;

    private List<MenuItemResponse> items;
}
