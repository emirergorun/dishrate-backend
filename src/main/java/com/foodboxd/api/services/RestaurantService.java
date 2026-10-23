package com.foodboxd.api.services;

import com.foodboxd.api.dtos.requests.CreateRestaurantRequest;
import com.foodboxd.api.dtos.requests.UpdateRestaurantRequest;
import com.foodboxd.api.dtos.responses.AddressResponse;
import com.foodboxd.api.dtos.responses.RestaurantResponse;
import com.foodboxd.api.entities.Category;
import com.foodboxd.api.entities.Address;
import com.foodboxd.api.entities.Restaurant;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.entities.UserRole;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.repositories.AddressRepository;
import com.foodboxd.api.repositories.CategoryRepository;
import com.foodboxd.api.repositories.MenuItemRepository;
import com.foodboxd.api.repositories.RestaurantRepository;
import com.foodboxd.api.utils.SearchText;
import com.foodboxd.api.utils.CategorySlug;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final AddressRepository addressRepository;
    private final MenuItemRepository menuItemRepository;
    private final CategoryRepository categoryRepository;

    // -----------------------------------------------------------------------
    // Create a restaurant
    // -----------------------------------------------------------------------
    @Transactional
    public RestaurantResponse createRestaurant(CreateRestaurantRequest request) {
        log.info("Create restaurant request. Name: {}", request.getName());
        Address address = addressRepository.findById(request.getAddressId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Adres bulunamadı."
                ));

        Restaurant restaurant = Restaurant.builder()
                .address(address)
                .name(request.getName())
                .logoUrl(request.getLogoUrl())
                .build();

        Restaurant saved = restaurantRepository.save(restaurant);
        log.info("Restaurant created successfully. ID: {}", saved.getRestaurantId());
        return toResponse(saved);
    }

    // -----------------------------------------------------------------------
    // Get restaurant by ID
    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public RestaurantResponse getRestaurantById(Long restaurantId) {
        log.debug("Fetching restaurant. ID: {}", restaurantId);
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restoran bulunamadı."
                ));
        return toResponse(restaurant);
    }

    // -----------------------------------------------------------------------
    // Get all restaurants
    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<RestaurantResponse> getAllRestaurants() {
        log.debug("Fetching all restaurants.");
        return toResponses(restaurantRepository.findAll());
    }

    // -----------------------------------------------------------------------
    // Search restaurants by name
    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<RestaurantResponse> searchByName(String name) {
        log.debug("Searching restaurants by name: {}", name);
        if (SearchText.normalize(name).isEmpty()) return List.of();
        return restaurantRepository.searchByNormalizedName(
                        SearchText.containsPattern(name), SearchText.SOURCE_CHARS, SearchText.TARGET_CHARS, 50)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Get restaurants by city
    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<RestaurantResponse> getByCity(String city) {
        log.debug("Fetching restaurants by city: {}", city);
        return toResponses(restaurantRepository.findByAddress_City(city));
    }

    // -----------------------------------------------------------------------
    // Get restaurants owned by a user (owner dashboard)
    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<RestaurantResponse> getMyRestaurants(User user) {
        log.debug("Fetching restaurants owned by user ID: {}", user.getUserId());
        // Sahip bilgisi yalnızca burada, sahibin kendisine gider (1.8).
        return restaurantRepository.findByOwnerUserId(user.getUserId())
                .stream()
                .map(r -> {
                    RestaurantResponse response = toResponse(r);
                    response.setOwnerId(user.getUserId());
                    return response;
                })
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Update restaurant info (owner/admin only)
    // -----------------------------------------------------------------------
    @Transactional
    public RestaurantResponse updateRestaurant(Long restaurantId,
                                               UpdateRestaurantRequest request,
                                               User currentUser) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restoran bulunamadı."));
        assertCanManage(currentUser, restaurantId);

        if (request.getName() != null && !request.getName().isBlank()) {
            restaurant.setName(request.getName().trim());
        }
        if (request.getLogoUrl() != null) {
            restaurant.setLogoUrl(request.getLogoUrl());
        }

        Address address = restaurant.getAddress();
        if (request.getCity() != null && !request.getCity().isBlank()) {
            address.setCity(request.getCity().trim());
        }
        if (request.getDistrict() != null) {
            address.setDistrict(request.getDistrict().trim());
        }
        if (request.getFullAddress() != null) {
            address.setFullAddress(request.getFullAddress());
        }
        if (request.getLatitude() != null) {
            address.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            address.setLongitude(request.getLongitude());
        }
        addressRepository.save(address);

        Restaurant saved = restaurantRepository.save(restaurant);
        log.info("Restaurant updated. ID: {}", restaurantId);
        return toResponse(saved);
    }

    // -----------------------------------------------------------------------
    // Delete a restaurant (owner/admin only)
    // -----------------------------------------------------------------------
    @Transactional
    public void deleteRestaurant(Long restaurantId, User currentUser) {
        log.info("Delete restaurant request. ID: {}", restaurantId);
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new ResourceNotFoundException(
                    "Restoran bulunamadı."
            );
        }
        assertCanManage(currentUser, restaurantId);
        restaurantRepository.deleteById(restaurantId);
        log.info("Restaurant deleted successfully. ID: {}", restaurantId);
    }

    // -----------------------------------------------------------------------
    // Yetki: kullanıcı bu restoranın sahibi mi (ya da admin mi)?
    // -----------------------------------------------------------------------
    public void assertCanManage(User user, Long restaurantId) {
        if (user.getRole().atLeast(UserRole.ADMIN)) return;

        // Rol kaba kapı, sahiplik asıl kontrol: owner rolü olan biri
        // BAŞKASININ restoranını yönetemez.
        boolean owns = restaurantRepository.findById(restaurantId)
                .map(r -> r.isOwnedBy(user.getUserId()))
                .orElse(false);
        if (!owns) {
            throw new AccessDeniedException("Bu restoran üzerinde yetkin yok.");
        }
    }

    // -----------------------------------------------------------------------
    // Public: Entity → Response DTO (used by other services)
    // -----------------------------------------------------------------------
    public RestaurantResponse toResponse(Restaurant restaurant) {
        Object[] top = primaryCategory(
                menuItemRepository.countCategoriesOfRestaurant(restaurant.getRestaurantId()));
        return toResponse(restaurant, top == null ? null : (String) top[0],
                top == null ? null : (String) top[1]);
    }

    /**
     * Liste için: kategori sayıları tek sorguda çekilir, restoran başına ayrı
     * sorgu atılmaz (harita tüm restoranları istiyor).
     */
    private List<RestaurantResponse> toResponses(List<Restaurant> restaurants) {
        Map<Long, List<Object[]>> counts = new HashMap<>();
        for (Object[] row : menuItemRepository.countCategoriesPerRestaurant()) {
            counts.computeIfAbsent((Long) row[0], k -> new ArrayList<>())
                    .add(new Object[]{row[1], row[2], row[3]});
        }
        return restaurants.stream()
                .map(r -> {
                    Object[] top = primaryCategory(counts.getOrDefault(r.getRestaurantId(), List.of()));
                    return toResponse(r, top == null ? null : (String) top[0],
                            top == null ? null : (String) top[1]);
                })
                .collect(Collectors.toList());
    }

    /**
     * [ad, anahtar, adet] satırlarından restoranın türü: en kalabalık kategori,
     * eşitlikte alfabetik ilk. Kahve, içecek ve atıştırmalık sayılmaz — her
     * menüde bulunan eşlikçiler; kebapçının içecek listesi uzun diye haritada
     * "içecek" görünmesin (22 Ekim). Menü yalnız bunlardan oluşuyorsa (kafe)
     * sayılırlar.
     */
    private static Object[] primaryCategory(List<Object[]> rows) {
        List<Object[]> main = rows.stream()
                .filter(s -> !CategorySlug.COMPANIONS.contains((String) s[1]))
                .toList();
        return (main.isEmpty() ? rows : main).stream()
                .max(Comparator.<Object[]>comparingLong(s -> (Long) s[2])
                        .thenComparing(s -> (String) s[0], Comparator.reverseOrder()))
                .orElse(null);
    }

    private RestaurantResponse toResponse(Restaurant restaurant, String categoryName, String categorySlug) {
        AddressResponse addressResponse = AddressResponse.builder()
                .addressId(restaurant.getAddress().getAddressId())
                .city(restaurant.getAddress().getCity())
                .district(restaurant.getAddress().getDistrict())
                .fullAddress(restaurant.getAddress().getFullAddress())
                .latitude(restaurant.getAddress().getLatitude())
                .longitude(restaurant.getAddress().getLongitude())
                .build();

        return RestaurantResponse.builder()
                .restaurantId(restaurant.getRestaurantId())
                .name(restaurant.getName())
                .logoUrl(restaurant.getLogoUrl())
                .address(addressResponse)
                .claimable(restaurant.isClaimable())
                .categoryName(categoryName)
                .categorySlug(categorySlug)
                .build();
    }
}
