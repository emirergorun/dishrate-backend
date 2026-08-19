package com.foodboxd.api.controllers;

import com.foodboxd.api.dtos.requests.CreateCategoryRequest;
import com.foodboxd.api.dtos.requests.CreateMenuItemRequest;
import com.foodboxd.api.dtos.requests.UpdateMenuItemRequest;
import com.foodboxd.api.dtos.responses.CategoryResponse;
import com.foodboxd.api.dtos.responses.MenuItemResponse;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.services.MenuItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
     * GET /api/v1/menu-items/categories
     * Lists all categories (menü öğesi formundaki kategori seçimi için).
     */
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        return ResponseEntity.ok(menuItemService.getAllCategories());
    }

    /**
     * POST /api/v1/menu-items
     * Creates a new menu item (owner/admin only).
     */
    @PostMapping
    public ResponseEntity<MenuItemResponse> createMenuItem(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateMenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(menuItemService.createMenuItem(request, currentUser));
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
     * GET /api/v1/menu-items
     * ?category= verilirse kategoriye göre, ?name= verilirse ada göre filtreler;
     * hiçbiri verilmezse tüm menü öğelerini döner (keşfet akışı).
     */
    @GetMapping
    public ResponseEntity<List<MenuItemResponse>> listMenuItems(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category) {
        if (category != null && !category.isBlank()) {
            return ResponseEntity.ok(menuItemService.getByCategory(category));
        }
        if (name != null && !name.isBlank()) {
            return ResponseEntity.ok(menuItemService.searchByName(name));
        }
        return ResponseEntity.ok(menuItemService.getAllMenuItems());
    }

    /**
     * PATCH /api/v1/menu-items/{menuItemId}
     * Updates a menu item (owner/admin only).
     */
    @PatchMapping("/{menuItemId}")
    public ResponseEntity<MenuItemResponse> updateMenuItem(
            @PathVariable Long menuItemId,
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpdateMenuItemRequest request) {
        return ResponseEntity.ok(
                menuItemService.updateMenuItem(menuItemId, request, currentUser));
    }

    /**
     * DELETE /api/v1/menu-items/{menuItemId}
     * Deletes a menu item (owner/admin only).
     */
    @DeleteMapping("/{menuItemId}")
    public ResponseEntity<Void> deleteMenuItem(
            @PathVariable Long menuItemId,
            @AuthenticationPrincipal User currentUser) {
        menuItemService.deleteMenuItem(menuItemId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
