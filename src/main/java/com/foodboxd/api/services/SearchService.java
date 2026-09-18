package com.foodboxd.api.services;

import com.foodboxd.api.dtos.responses.MenuItemResponse;
import com.foodboxd.api.dtos.responses.SearchResultResponse;
import com.foodboxd.api.entities.MenuItem;
import com.foodboxd.api.entities.Restaurant;
import com.foodboxd.api.repositories.MenuItemRepository;
import com.foodboxd.api.repositories.RestaurantRepository;
import com.foodboxd.api.utils.SearchText;
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
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_ITEMS = 300;
    private static final int MAX_RESTAURANTS = 50;
    /** Adı eşleşen restoranlarda kartta gösterilen menü uzunluğu. */
    private static final int NAME_MATCH_MENU_LIMIT = 20;

    private final MenuItemRepository menuItemRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuItemService menuItemService;

    @Transactional(readOnly = true)
    public List<SearchResultResponse> search(String q) {
        if (SearchText.normalize(q).length() < MIN_QUERY_LENGTH) return List.of();
        String pattern = SearchText.containsPattern(q);

        Map<Long, SearchResultResponse> result = new LinkedHashMap<>();

        // 1) Adı eşleşen restoranlar önce — kullanıcı büyük ihtimalle onu arıyor.
        for (Restaurant r : restaurantRepository.searchByNormalizedName(
                pattern, SearchText.SOURCE_CHARS, SearchText.TARGET_CHARS, MAX_RESTAURANTS)) {
            List<MenuItemResponse> menu = menuItemRepository
                    .findByRestaurant_RestaurantId(r.getRestaurantId()).stream()
                    .sorted(Comparator.comparing(MenuItem::getAverageRating).reversed())
                    .limit(NAME_MATCH_MENU_LIMIT)
                    .map(menuItemService::toResponse)
                    .toList();
            result.put(r.getRestaurantId(), entry(r, true, new ArrayList<>(menu)));
        }

        // 2) Yemeği ya da kategorisi eşleşenler, puana göre.
        for (MenuItem mi : menuItemRepository.search(
                pattern, SearchText.SOURCE_CHARS, SearchText.TARGET_CHARS, MAX_ITEMS)) {
            Restaurant r = mi.getRestaurant();
            SearchResultResponse entry = result.get(r.getRestaurantId());
            if (entry == null) {
                if (result.size() >= MAX_RESTAURANTS) continue;
                entry = entry(r, false, new ArrayList<>());
                result.put(r.getRestaurantId(), entry);
            }
            if (!entry.isNameMatched()) {
                entry.getItems().add(menuItemService.toResponse(mi));
            }
        }
        return new ArrayList<>(result.values());
    }

    private SearchResultResponse entry(Restaurant r, boolean nameMatch,
                                      List<MenuItemResponse> items) {
        var address = r.getAddress();
        return SearchResultResponse.builder()
                .restaurantId(r.getRestaurantId())
                .name(r.getName())
                .city(address != null ? address.getCity() : null)
                .district(address != null ? address.getDistrict() : null)
                .latitude(address != null ? address.getLatitude() : null)
                .longitude(address != null ? address.getLongitude() : null)
                .nameMatched(nameMatch)
                .items(items)
                .build();
    }
}
