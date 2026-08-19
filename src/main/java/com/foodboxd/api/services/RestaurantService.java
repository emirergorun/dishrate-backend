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
import com.foodboxd.api.repositories.RestaurantOwnerRepository;
import com.foodboxd.api.repositories.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final AddressRepository addressRepository;
    private final RestaurantOwnerRepository restaurantOwnerRepository;

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
        return restaurantRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Search restaurants by name
    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<RestaurantResponse> searchByName(String name) {
        log.debug("Searching restaurants by name: {}", name);
        return restaurantRepository.findByNameContainingIgnoreCase(name)
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
        return restaurantRepository.findByAddress_City(city)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Get restaurants owned by a user (owner dashboard)
    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<RestaurantResponse> getMyRestaurants(User user) {
        log.debug("Fetching restaurants owned by user ID: {}", user.getUserId());
        return restaurantOwnerRepository.findByUserUserId(user.getUserId())
                .stream()
                .map(owner -> toResponse(owner.getRestaurant()))
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
        if (user.getRole() == UserRole.ADMIN) return;
        boolean owns = restaurantOwnerRepository
                .findByRestaurantRestaurantIdAndUserUserId(restaurantId, user.getUserId())
                .isPresent();
        if (!owns) {
            throw new AccessDeniedException("Bu restoran üzerinde yetkiniz yok.");
        }
    }

    // -----------------------------------------------------------------------
    // Public: Entity → Response DTO (used by other services)
    // -----------------------------------------------------------------------
    public RestaurantResponse toResponse(Restaurant restaurant) {
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
                .ownershipStatus(restaurant.getOwnershipStatus())
                .coOwnershipEnabled(restaurant.isCoOwnershipEnabled())
                .build();
    }
}
