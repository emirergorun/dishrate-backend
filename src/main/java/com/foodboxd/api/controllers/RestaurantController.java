package com.foodboxd.api.controllers;

import com.foodboxd.api.dtos.requests.CreateAddressRequest;
import com.foodboxd.api.dtos.requests.CreateRestaurantRequest;
import com.foodboxd.api.dtos.requests.UpdateRestaurantRequest;
import com.foodboxd.api.dtos.responses.AddressResponse;
import com.foodboxd.api.dtos.responses.MenuItemResponse;
import com.foodboxd.api.dtos.responses.RestaurantResponse;
import com.foodboxd.api.entities.Address;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.repositories.AddressRepository;
import com.foodboxd.api.services.MenuItemService;
import com.foodboxd.api.services.RestaurantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final MenuItemService menuItemService;
    private final AddressRepository addressRepository;

    /**
     * POST /api/v1/restaurants/addresses
     * Saves a new address (required before creating a restaurant).
     */
    @PostMapping("/addresses")
    public ResponseEntity<AddressResponse> createAddress(@Valid @RequestBody CreateAddressRequest request) {
        Address newAddress = Address.builder()
                .city(request.getCity())
                .district(request.getDistrict())
                .fullAddress(request.getFullAddress())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();

        Address saved = addressRepository.save(newAddress);

        AddressResponse response = AddressResponse.builder()
                .addressId(saved.getAddressId())
                .city(saved.getCity())
                .district(saved.getDistrict())
                .fullAddress(saved.getFullAddress())
                .latitude(saved.getLatitude())
                .longitude(saved.getLongitude())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/restaurants
     * Creates a new restaurant.
     */
    @PostMapping
    public ResponseEntity<RestaurantResponse> createRestaurant(@Valid @RequestBody CreateRestaurantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(restaurantService.createRestaurant(request));
    }

    /**
     * GET /api/v1/restaurants/mine
     * Giriş yapmış kullanıcının sahip olduğu restoranlar (owner dashboard).
     */
    @GetMapping("/mine")
    public ResponseEntity<List<RestaurantResponse>> myRestaurants(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(restaurantService.getMyRestaurants(currentUser));
    }

    /**
     * GET /api/v1/restaurants/{restaurantId}
     * Returns restaurant details by ID.
     */
    @GetMapping("/{restaurantId}")
    public ResponseEntity<RestaurantResponse> getRestaurantById(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(restaurantService.getRestaurantById(restaurantId));
    }

    /**
     * GET /api/v1/restaurants
     * Lists all restaurants. Optional filters: ?name= or ?city=
     */
    @GetMapping
    public ResponseEntity<List<RestaurantResponse>> listRestaurants(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String city) {

        if (name != null && !name.isBlank()) {
            return ResponseEntity.ok(restaurantService.searchByName(name));
        }
        if (city != null && !city.isBlank()) {
            return ResponseEntity.ok(restaurantService.getByCity(city));
        }
        return ResponseEntity.ok(restaurantService.getAllRestaurants());
    }

    /**
     * GET /api/v1/restaurants/{restaurantId}/menu
     * Returns the menu items for a given restaurant.
     */
    @GetMapping("/{restaurantId}/menu")
    public ResponseEntity<List<MenuItemResponse>> getRestaurantMenu(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(menuItemService.getMenuByRestaurant(restaurantId));
    }

    /**
     * PATCH /api/v1/restaurants/{restaurantId}
     * Updates restaurant info (owner/admin only).
     */
    @PatchMapping("/{restaurantId}")
    public ResponseEntity<RestaurantResponse> updateRestaurant(
            @PathVariable Long restaurantId,
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpdateRestaurantRequest request) {
        return ResponseEntity.ok(
                restaurantService.updateRestaurant(restaurantId, request, currentUser));
    }

    /**
     * DELETE /api/v1/restaurants/{restaurantId}
     * Deletes a restaurant (owner/admin only).
     */
    @DeleteMapping("/{restaurantId}")
    public ResponseEntity<Void> deleteRestaurant(
            @PathVariable Long restaurantId,
            @AuthenticationPrincipal User currentUser) {
        restaurantService.deleteRestaurant(restaurantId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
