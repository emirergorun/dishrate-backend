package com.foodboxd.api.controllers;

import com.foodboxd.api.dtos.requests.CreateRatingRequest;
import com.foodboxd.api.dtos.responses.MenuItemReviewResponse;
import com.foodboxd.api.dtos.responses.RatingResponse;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.services.RatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ratings")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    /**
     * POST /api/v1/ratings
     * Creates or updates a rating (UPSERT).
     * If the same user rates the same menu item again, the existing record is updated.
     * The menu item's average_rating is recalculated automatically after every operation.
     */
    @PostMapping
    public ResponseEntity<RatingResponse> upsertRating(@Valid @RequestBody CreateRatingRequest request) {
        RatingResponse response = ratingService.upsertRating(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * GET /api/v1/ratings/menu-item/{menuItemId}
     * Returns all ratings for a given menu item.
     */
    @GetMapping("/menu-item/{menuItemId}")
    public ResponseEntity<List<MenuItemReviewResponse>> getRatingsByMenuItem(
            @PathVariable Long menuItemId,
            @AuthenticationPrincipal User viewer) {
        return ResponseEntity.ok(ratingService.getRatingsByMenuItem(menuItemId, viewer));
    }

    /**
     * GET /api/v1/ratings/user/{userId}
     * Returns all ratings submitted by a given user.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<RatingResponse>> getRatingsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ratingService.getRatingsByUser(userId));
    }

    /**
     * DELETE /api/v1/ratings/{ratingId}
     * Deletes a rating and recalculates the menu item's average.
     */
    @DeleteMapping("/{ratingId}")
    public ResponseEntity<Void> deleteRating(@PathVariable Long ratingId) {
        ratingService.deleteRating(ratingId);
        return ResponseEntity.noContent().build();
    }
}
