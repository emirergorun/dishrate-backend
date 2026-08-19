package com.foodboxd.api.services;

import com.foodboxd.api.dtos.requests.CreateCategoryRequest;
import com.foodboxd.api.dtos.requests.CreateMenuItemRequest;
import com.foodboxd.api.dtos.requests.UpdateMenuItemRequest;
import com.foodboxd.api.dtos.responses.CategoryResponse;
import com.foodboxd.api.dtos.responses.MenuItemResponse;
import com.foodboxd.api.entities.Category;
import com.foodboxd.api.entities.MenuItem;
import com.foodboxd.api.entities.Restaurant;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.entities.UserRole;
import com.foodboxd.api.exceptions.ResourceAlreadyExistsException;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.repositories.CategoryRepository;
import com.foodboxd.api.repositories.MenuItemRepository;
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
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final RestaurantRepository restaurantRepository;
    private final CategoryRepository categoryRepository;
    private final RestaurantOwnerRepository restaurantOwnerRepository;

    // -----------------------------------------------------------------------
    // Create a category
    // -----------------------------------------------------------------------
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        log.info("Create category request. Name: {}", request.getName());
        if (categoryRepository.existsByName(request.getName())) {
            throw new ResourceAlreadyExistsException(
                    "Category already exists: " + request.getName()
            );
        }
        Category category = Category.builder().name(request.getName()).build();
        Category saved = categoryRepository.save(category);
        log.info("Category created successfully. ID: {}", saved.getCategoryId());
        return CategoryResponse.builder()
                .categoryId(saved.getCategoryId())
                .name(saved.getName())
                .build();
    }

    // -----------------------------------------------------------------------
    // Create a menu item
    // -----------------------------------------------------------------------
    @Transactional
    public MenuItemResponse createMenuItem(CreateMenuItemRequest request, User currentUser) {
        log.info("Create menu item request. Restaurant ID: {}, Name: {}",
                request.getRestaurantId(), request.getName());

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant not found. ID: " + request.getRestaurantId()
                ));
        assertCanManage(currentUser, restaurant.getRestaurantId());

        if (menuItemRepository.existsByRestaurant_RestaurantIdAndName(
                request.getRestaurantId(), request.getName())) {
            throw new ResourceAlreadyExistsException(
                    "Menu item '" + request.getName() + "' already exists in this restaurant."
            );
        }

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Category not found. ID: " + request.getCategoryId()
                    ));
        }

        MenuItem menuItem = MenuItem.builder()
                .restaurant(restaurant)
                .category(category)
                .name(request.getName())
                .price(request.getPrice())
                .photoUrl(request.getPhotoUrl())
                .build();

        MenuItem saved = menuItemRepository.save(menuItem);
        log.info("Menu item created successfully. ID: {}", saved.getMenuItemId());
        return toResponse(saved);
    }

    // -----------------------------------------------------------------------
    // Get menu item by ID
    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public MenuItemResponse getMenuItemById(Long menuItemId) {
        log.debug("Fetching menu item. ID: {}", menuItemId);
        MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Menu item not found. ID: " + menuItemId
                ));
        return toResponse(menuItem);
    }

    // -----------------------------------------------------------------------
    // Get all menu items for a restaurant
    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getMenuByRestaurant(Long restaurantId) {
        log.debug("Fetching menu for restaurant ID: {}", restaurantId);
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new ResourceNotFoundException(
                    "Restaurant not found. ID: " + restaurantId
            );
        }
        return menuItemRepository.findByRestaurant_RestaurantId(restaurantId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Search menu items by name
    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getAllMenuItems() {
        log.debug("Fetching all menu items.");
        return menuItemRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MenuItemResponse> getByCategory(String categoryName) {
        log.debug("Fetching menu items by category: {}", categoryName);
        return menuItemRepository.findByCategory_NameIgnoreCase(categoryName)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MenuItemResponse> searchByName(String name) {
        log.debug("Searching menu items by name: {}", name);
        return menuItemRepository.findByNameContainingIgnoreCase(name)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Update a menu item (owner/admin only)
    // -----------------------------------------------------------------------
    @Transactional
    public MenuItemResponse updateMenuItem(Long menuItemId,
                                           UpdateMenuItemRequest request,
                                           User currentUser) {
        MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Menu item not found. ID: " + menuItemId));
        assertCanManage(currentUser, menuItem.getRestaurant().getRestaurantId());

        if (request.getName() != null && !request.getName().isBlank()) {
            menuItem.setName(request.getName().trim());
        }
        if (request.getPrice() != null) {
            menuItem.setPrice(request.getPrice());
        }
        if (request.getPhotoUrl() != null) {
            menuItem.setPhotoUrl(request.getPhotoUrl());
        }
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Category not found. ID: " + request.getCategoryId()));
            menuItem.setCategory(category);
        }

        MenuItem saved = menuItemRepository.save(menuItem);
        log.info("Menu item updated. ID: {}", menuItemId);
        return toResponse(saved);
    }

    // -----------------------------------------------------------------------
    // List all categories (dropdown için)
    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll()
                .stream()
                .map(c -> CategoryResponse.builder()
                        .categoryId(c.getCategoryId())
                        .name(c.getName())
                        .build())
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Delete a menu item (owner/admin only)
    // -----------------------------------------------------------------------
    @Transactional
    public void deleteMenuItem(Long menuItemId, User currentUser) {
        log.info("Delete menu item request. ID: {}", menuItemId);
        MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Menu item not found for deletion. ID: " + menuItemId));
        assertCanManage(currentUser, menuItem.getRestaurant().getRestaurantId());
        menuItemRepository.delete(menuItem);
        log.info("Menu item deleted successfully. ID: {}", menuItemId);
    }

    // -----------------------------------------------------------------------
    // Yetki: kullanıcı bu restoranın sahibi mi (ya da admin mi)?
    // -----------------------------------------------------------------------
    private void assertCanManage(User user, Long restaurantId) {
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
    public MenuItemResponse toResponse(MenuItem menuItem) {
        CategoryResponse categoryResponse = null;
        if (menuItem.getCategory() != null) {
            categoryResponse = CategoryResponse.builder()
                    .categoryId(menuItem.getCategory().getCategoryId())
                    .name(menuItem.getCategory().getName())
                    .build();
        }

        return MenuItemResponse.builder()
                .menuItemId(menuItem.getMenuItemId())
                .name(menuItem.getName())
                .price(menuItem.getPrice())
                .averageRating(menuItem.getAverageRating())
                .photoUrl(menuItem.getPhotoUrl())
                .restaurantId(menuItem.getRestaurant().getRestaurantId())
                .restaurantName(menuItem.getRestaurant().getName())
                .city(menuItem.getRestaurant().getAddress().getCity())
                .district(menuItem.getRestaurant().getAddress().getDistrict())
                .restaurantLatitude(menuItem.getRestaurant().getAddress().getLatitude())
                .restaurantLongitude(menuItem.getRestaurant().getAddress().getLongitude())
                .category(categoryResponse)
                .build();
    }
}
