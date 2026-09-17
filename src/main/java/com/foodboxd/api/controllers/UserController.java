package com.foodboxd.api.controllers;

import com.foodboxd.api.dtos.requests.ChangePasswordRequest;
import com.foodboxd.api.dtos.requests.DeleteAccountRequest;
import com.foodboxd.api.dtos.requests.UpdateUserRequest;
import com.foodboxd.api.dtos.responses.UserResponse;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.security.Yetki;
import com.foodboxd.api.services.AccountDeletionService;
import com.foodboxd.api.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Kullanıcının kendi hesabı. Her uç nokta yalnızca hesabın sahibine açık;
 * kullanıcı listesi ve başkasının hesabı üzerindeki işlemler /admin altında.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccountDeletionService accountDeletionService;

    // NOT: Kayıt işlemi /auth/register endpoint'inden yapılır.

    /**
     * GET /api/v1/users/{userId}
     * Returns user details by ID.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser) {
        Yetki.kendisi(currentUser, userId);
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    /**
     * PATCH /api/v1/users/{userId}
     * Updates user profile fields (bio, profile photo).
     */
    @PatchMapping("/{userId}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpdateUserRequest request) {
        Yetki.kendisi(currentUser, userId);
        return ResponseEntity.ok(userService.updateUser(userId, request));
    }

    /**
     * PATCH /api/v1/users/{userId}/password
     * Changes the user's password (requires current password).
     */
    @PatchMapping("/{userId}/password")
    public ResponseEntity<Void> changePassword(
            @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody ChangePasswordRequest request) {
        Yetki.kendisi(currentUser, userId);
        userService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/v1/users/me
     * Giriş yapmış kullanıcının hesabını ve bütün verisini kalıcı olarak siler.
     * Şifre yeniden istenir; yanlışsa 409.
     */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteOwnAccount(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody DeleteAccountRequest request) {
        accountDeletionService.deleteOwnAccount(currentUser, request.getPassword());
        return ResponseEntity.noContent().build();
    }
}
