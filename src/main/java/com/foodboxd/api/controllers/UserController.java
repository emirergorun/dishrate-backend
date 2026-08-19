package com.foodboxd.api.controllers;

import com.foodboxd.api.dtos.requests.ChangePasswordRequest;
import com.foodboxd.api.dtos.requests.UpdateUserRequest;
import com.foodboxd.api.dtos.responses.UserResponse;
import com.foodboxd.api.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // NOT: Kayıt işlemi /auth/register endpoint'inden yapılır.

    /**
     * GET /api/v1/users/{userId}
     * Returns user details by ID.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    /**
     * GET /api/v1/users
     * Returns a list of all users.
     */
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * PATCH /api/v1/users/{userId}
     * Updates user profile fields (bio, profile photo).
     */
    @PatchMapping("/{userId}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(userId, request));
    }

    /**
     * PATCH /api/v1/users/{userId}/password
     * Changes the user's password (requires current password).
     */
    @PatchMapping("/{userId}/password")
    public ResponseEntity<Void> changePassword(
            @PathVariable Long userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/v1/users/{userId}
     * Deletes a user account.
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
