package com.foodboxd.api.services;

import com.foodboxd.api.dtos.requests.CreateRestaurantRequest;
import com.foodboxd.api.dtos.requests.UpdateRestaurantRequest;
import com.foodboxd.api.dtos.responses.AddressResponse;
import com.foodboxd.api.dtos.responses.RestaurantResponse;
import com.foodboxd.api.entities.Address;
import com.foodboxd.api.entities.Restaurant;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.entities.UserRole;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.repositories.AddressRepository;
import com.foodboxd.api.repositories.MenuItemRepository;
import com.foodboxd.api.repositories.RestaurantRepository;
import com.foodboxd.api.utils.AramaMetni;
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

    // -----------------------------------------------------------------------
    // Create a restaurant
    // -----------------------------------------------------------------------
    @Transactional
    public RestaurantResponse createRestaurant(CreateRestaurantRequest request) {
        log.info("Create restaurant request. Name: {}", request.getName());
        Address address = addressRepository.findById(request.getAddressId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Address not found. ID: " + request.getAddressId()
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
                        "Restaurant not found. ID: " + restaurantId
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
        if (AramaMetni.normalize(name).isEmpty()) return List.of();
        return restaurantRepository.searchByNormalizedName(
                        AramaMetni.icerir(name), AramaMetni.KAYNAK, AramaMetni.HEDEF, 50)
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
        return restaurantRepository.findByOwnerUserId(user.getUserId())
                .stream()
                .map(this::toResponse)
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
                        "Restaurant not found. ID: " + restaurantId));
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
                    "Restaurant not found for deletion. ID: " + restaurantId
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
            throw new AccessDeniedException("Bu restoran üzerinde yetkiniz yok.");
        }
    }

    // -----------------------------------------------------------------------
    // Public: Entity → Response DTO (used by other services)
    // -----------------------------------------------------------------------
    public RestaurantResponse toResponse(Restaurant restaurant) {
        return toResponse(restaurant, anaKategori(
                menuItemRepository.countCategoriesOfRestaurant(restaurant.getRestaurantId()), 0));
    }

    /**
     * Liste için: kategori sayıları tek sorguda çekilir, restoran başına ayrı
     * sorgu atılmaz (harita tüm restoranları istiyor).
     */
    private List<RestaurantResponse> toResponses(List<Restaurant> restaurants) {
        Map<Long, List<Object[]>> sayilar = new HashMap<>();
        for (Object[] satir : menuItemRepository.countCategoriesPerRestaurant()) {
            sayilar.computeIfAbsent((Long) satir[0], k -> new ArrayList<>())
                    .add(new Object[]{satir[1], satir[2]});
        }
        return restaurants.stream()
                .map(r -> toResponse(r, anaKategori(
                        sayilar.getOrDefault(r.getRestaurantId(), List.of()), 0)))
                .collect(Collectors.toList());
    }

    /** [kategori, adet] satırlarından en kalabalık kategori; eşitlikte alfabetik ilk. */
    private static String anaKategori(List<Object[]> satirlar, int adIndeksi) {
        return satirlar.stream()
                .max(Comparator.<Object[]>comparingLong(s -> (Long) s[adIndeksi + 1])
                        .thenComparing(s -> (String) s[adIndeksi], Comparator.reverseOrder()))
                .map(s -> (String) s[adIndeksi])
                .orElse(null);
    }

    private RestaurantResponse toResponse(Restaurant restaurant, String categoryName) {
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
                .ownerId(restaurant.getOwner() != null
                        ? restaurant.getOwner().getUserId()
                        : null)
                .claimable(restaurant.isClaimable())
                .categoryName(categoryName)
                .build();
    }
}
