package com.foodboxd.api.services;

import com.foodboxd.api.dtos.requests.CreateCategoryRequest;
import com.foodboxd.api.dtos.requests.CreateMenuItemRequest;
import com.foodboxd.api.dtos.responses.CategoryResponse;
import com.foodboxd.api.dtos.responses.MenuItemResponse;
import com.foodboxd.api.entities.Category;
import com.foodboxd.api.entities.MenuItem;
import com.foodboxd.api.entities.Restaurant;
import com.foodboxd.api.exceptions.ResourceAlreadyExistsException;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.repositories.CategoryRepository;
import com.foodboxd.api.repositories.MenuItemRepository;
import com.foodboxd.api.repositories.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public MenuItemResponse createMenuItem(CreateMenuItemRequest request) {
        log.info("Create menu item request. Restaurant ID: {}, Name: {}",
                request.getRestaurantId(), request.getName());

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant not found. ID: " + request.getRestaurantId()
                ));

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
    public List<MenuItemResponse> searchByName(String name) {
        log.debug("Searching menu items by name: {}", name);
        return menuItemRepository.findByNameContainingIgnoreCase(name)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Delete a menu item
    // -----------------------------------------------------------------------
    @Transactional
    public void deleteMenuItem(Long menuItemId) {
        log.info("Delete menu item request. ID: {}", menuItemId);
        if (!menuItemRepository.existsById(menuItemId)) {
            throw new ResourceNotFoundException(
                    "Menu item not found for deletion. ID: " + menuItemId
            );
        }
        menuItemRepository.deleteById(menuItemId);
        log.info("Menu item deleted successfully. ID: {}", menuItemId);
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
                .category(categoryResponse)
                .build();
    }
}
