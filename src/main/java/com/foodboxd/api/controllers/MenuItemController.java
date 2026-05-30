package com.foodboxd.api.controllers;

import com.foodboxd.api.dtos.requests.CreateCategoryRequest;
import com.foodboxd.api.dtos.requests.CreateMenuItemRequest;
import com.foodboxd.api.dtos.responses.CategoryResponse;
import com.foodboxd.api.dtos.responses.MenuItemResponse;
import com.foodboxd.api.services.MenuItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/menu-items")
@RequiredArgsConstructor
public class MenuItemController {

    private final MenuItemService menuItemService;

    /**
     * POST /api/v1/menu-items/categories
     * Creates a new category.
     */
    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(menuItemService.createCategory(request));
    }

    /**
     * POST /api/v1/menu-items
     * Creates a new menu item.
     */
    @PostMapping
    public ResponseEntity<MenuItemResponse> createMenuItem(@Valid @RequestBody CreateMenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(menuItemService.createMenuItem(request));
    }

    /**
     * GET /api/v1/menu-items/{menuItemId}
     * Returns a menu item by ID.
     */
    @GetMapping("/{menuItemId}")
    public ResponseEntity<MenuItemResponse> getMenuItemById(@PathVariable Long menuItemId) {
        return ResponseEntity.ok(menuItemService.getMenuItemById(menuItemId));
    }

    /**
     * GET /api/v1/menu-items?name=...
     * Searches menu items by name.
     */
    @GetMapping
    public ResponseEntity<List<MenuItemResponse>> searchMenuItems(@RequestParam(required = false) String name) {
        if (name != null && !name.isBlank()) {
            return ResponseEntity.ok(menuItemService.searchByName(name));
        }
        return ResponseEntity.ok(List.of());
    }

    /**
     * DELETE /api/v1/menu-items/{menuItemId}
     * Deletes a menu item.
     */
    @DeleteMapping("/{menuItemId}")
    public ResponseEntity<Void> deleteMenuItem(@PathVariable Long menuItemId) {
        menuItemService.deleteMenuItem(menuItemId);
        return ResponseEntity.noContent().build();
    }
}
