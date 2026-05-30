package com.foodboxd.api.controllers;

import com.foodboxd.api.dtos.requests.CreateWishlistItemRequest;
import com.foodboxd.api.dtos.responses.WishlistItemResponse;
import com.foodboxd.api.services.WishlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    /**
     * POST /api/v1/wishlist
     * Adds a menu item to the user's wishlist.
     */
    @PostMapping
    public ResponseEntity<WishlistItemResponse> addToWishlist(
            @Valid @RequestBody CreateWishlistItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(wishlistService.addToWishlist(request));
    }

    /**
     * GET /api/v1/wishlist/user/{userId}
     * Returns the wishlist for a given user.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<WishlistItemResponse>> getWishlistByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(wishlistService.getWishlistByUser(userId));
    }

    /**
     * DELETE /api/v1/wishlist/{wishId}
     * Removes a wishlist item by its ID.
     */
    @DeleteMapping("/{wishId}")
    public ResponseEntity<Void> removeFromWishlist(@PathVariable Long wishId) {
        wishlistService.removeFromWishlist(wishId);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/v1/wishlist/user/{userId}/menu-item/{menuItemId}
     * Removes a wishlist item by user + menu item combination.
     */
    @DeleteMapping("/user/{userId}/menu-item/{menuItemId}")
    public ResponseEntity<Void> removeFromWishlistByItem(
            @PathVariable Long userId,
            @PathVariable Long menuItemId) {
        wishlistService.removeFromWishlistByItem(userId, menuItemId);
        return ResponseEntity.noContent().build();
    }
}
