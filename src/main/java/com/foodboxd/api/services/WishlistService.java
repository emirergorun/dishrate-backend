package com.foodboxd.api.services;

import com.foodboxd.api.dtos.requests.CreateWishlistItemRequest;
import com.foodboxd.api.dtos.responses.WishlistItemResponse;
import com.foodboxd.api.entities.MenuItem;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.entities.WishlistItem;
import com.foodboxd.api.exceptions.ResourceAlreadyExistsException;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.repositories.MenuItemRepository;
import com.foodboxd.api.repositories.UserRepository;
import com.foodboxd.api.repositories.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistItemRepository wishlistItemRepository;
    private final UserRepository userRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuItemService menuItemService;

    // -----------------------------------------------------------------------
    // Add item to wishlist
    // -----------------------------------------------------------------------
    @Transactional
    public WishlistItemResponse addToWishlist(CreateWishlistItemRequest request) {
        log.info("Add to wishlist request. User ID: {}, Menu Item ID: {}",
                request.getUserId(), request.getMenuItemId());

        if (wishlistItemRepository.existsByUser_UserIdAndMenuItem_MenuItemId(
                request.getUserId(), request.getMenuItemId())) {
            throw new ResourceAlreadyExistsException(
                    "Menu item already in wishlist. Menu Item ID: " + request.getMenuItemId()
            );
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found. ID: " + request.getUserId()
                ));

        MenuItem menuItem = menuItemRepository.findById(request.getMenuItemId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Menu item not found. ID: " + request.getMenuItemId()
                ));

        WishlistItem wishlistItem = WishlistItem.builder()
                .user(user)
                .menuItem(menuItem)
                .build();

        WishlistItem saved = wishlistItemRepository.save(wishlistItem);
        log.info("Item added to wishlist. Wish ID: {}", saved.getWishId());
        return toResponse(saved);
    }

    // -----------------------------------------------------------------------
    // Get wishlist by user
    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<WishlistItemResponse> getWishlistByUser(Long userId) {
        log.debug("Fetching wishlist for user ID: {}", userId);
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException(
                    "User not found. ID: " + userId
            );
        }
        return wishlistItemRepository.findByUser_UserId(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Remove from wishlist by wish ID
    // -----------------------------------------------------------------------
    @Transactional
    public void removeFromWishlist(Long wishId) {
        log.info("Remove from wishlist request. Wish ID: {}", wishId);
        if (!wishlistItemRepository.existsById(wishId)) {
            throw new ResourceNotFoundException(
                    "Wishlist item not found. ID: " + wishId
            );
        }
        wishlistItemRepository.deleteById(wishId);
        log.info("Item removed from wishlist. Wish ID: {}", wishId);
    }

    // -----------------------------------------------------------------------
    // Remove from wishlist by user + menu item combination
    // -----------------------------------------------------------------------
    @Transactional
    public void removeFromWishlistByItem(Long userId, Long menuItemId) {
        log.info("Remove from wishlist by item. User ID: {}, Menu Item ID: {}", userId, menuItemId);
        if (!wishlistItemRepository.existsByUser_UserIdAndMenuItem_MenuItemId(userId, menuItemId)) {
            throw new ResourceNotFoundException(
                    "Menu item not found in wishlist. Menu Item ID: " + menuItemId
            );
        }
        wishlistItemRepository.deleteByUser_UserIdAndMenuItem_MenuItemId(userId, menuItemId);
        log.info("Item removed from wishlist. User ID: {}, Menu Item ID: {}", userId, menuItemId);
    }

    // -----------------------------------------------------------------------
    // Private: Entity → Response DTO
    // -----------------------------------------------------------------------
    private WishlistItemResponse toResponse(WishlistItem item) {
        return WishlistItemResponse.builder()
                .wishId(item.getWishId())
                .userId(item.getUser().getUserId())
                .menuItem(menuItemService.toResponse(item.getMenuItem()))
                .build();
    }
}
