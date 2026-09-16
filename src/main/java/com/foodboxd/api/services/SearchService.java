package com.foodboxd.api.services;

import com.foodboxd.api.dtos.responses.MenuItemResponse;
import com.foodboxd.api.dtos.responses.SearchResultResponse;
import com.foodboxd.api.entities.MenuItem;
import com.foodboxd.api.entities.Restaurant;
import com.foodboxd.api.repositories.MenuItemRepository;
import com.foodboxd.api.repositories.RestaurantRepository;
import com.foodboxd.api.utils.AramaMetni;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Arama ekranının tek sorgusu: yemek, restoran ve kategori adında arar,
 * sonucu restorana göre gruplar.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    /** Bu uzunluktan kısa sorgular her şeyle eşleşir; hiç aranmaz. */
    private static final int EN_KISA = 2;
    private static final int EN_FAZLA_YEMEK = 300;
    private static final int EN_FAZLA_RESTORAN = 50;
    /** Adı eşleşen restoranlarda kartta gösterilen menü uzunluğu. */
    private static final int AD_ESLESMESI_MENU = 20;

    private final MenuItemRepository menuItemRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuItemService menuItemService;

    @Transactional(readOnly = true)
    public List<SearchResultResponse> search(String q) {
        if (AramaMetni.normalize(q).length() < EN_KISA) return List.of();
        String kalip = AramaMetni.icerir(q);

        Map<Long, SearchResultResponse> sonuc = new LinkedHashMap<>();

        // 1) Adı eşleşen restoranlar önce — kullanıcı büyük ihtimalle onu arıyor.
        for (Restaurant r : restaurantRepository.searchByNormalizedName(
                kalip, AramaMetni.KAYNAK, AramaMetni.HEDEF, EN_FAZLA_RESTORAN)) {
            List<MenuItemResponse> menu = menuItemRepository
                    .findByRestaurant_RestaurantId(r.getRestaurantId()).stream()
                    .sorted(Comparator.comparing(MenuItem::getAverageRating).reversed())
                    .limit(AD_ESLESMESI_MENU)
                    .map(menuItemService::toResponse)
                    .toList();
            sonuc.put(r.getRestaurantId(), kart(r, true, new ArrayList<>(menu)));
        }

        // 2) Yemeği ya da kategorisi eşleşenler, puana göre.
        for (MenuItem mi : menuItemRepository.search(
                kalip, AramaMetni.KAYNAK, AramaMetni.HEDEF, EN_FAZLA_YEMEK)) {
            Restaurant r = mi.getRestaurant();
            SearchResultResponse kart = sonuc.get(r.getRestaurantId());
            if (kart == null) {
                if (sonuc.size() >= EN_FAZLA_RESTORAN) continue;
                kart = kart(r, false, new ArrayList<>());
                sonuc.put(r.getRestaurantId(), kart);
            }
            if (!kart.isNameMatched()) {
                kart.getItems().add(menuItemService.toResponse(mi));
            }
        }
        return new ArrayList<>(sonuc.values());
    }

    private SearchResultResponse kart(Restaurant r, boolean adEslesti,
                                      List<MenuItemResponse> items) {
        var adres = r.getAddress();
        return SearchResultResponse.builder()
                .restaurantId(r.getRestaurantId())
                .name(r.getName())
                .city(adres != null ? adres.getCity() : null)
                .district(adres != null ? adres.getDistrict() : null)
                .latitude(adres != null ? adres.getLatitude() : null)
                .longitude(adres != null ? adres.getLongitude() : null)
                .nameMatched(adEslesti)
                .items(items)
                .build();
    }
}
