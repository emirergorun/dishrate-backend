package com.foodboxd.api.controllers;

import com.foodboxd.api.dtos.requests.ReviewClaimRequest;
import com.foodboxd.api.dtos.responses.RestaurantClaimResponse;
import com.foodboxd.api.dtos.responses.UserResponse;
import com.foodboxd.api.entities.UserRole;
import com.foodboxd.api.services.AdminService;
import com.foodboxd.api.services.ClaimService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ClaimService claimService;

    // ── Sahiplik Talepleri ────────────────────────────────────────────────────

    /**
     * GET /api/v1/admin/claims
     * Sahiplik talepleri. Varsayılan olarak yalnızca bekleyenler döner;
     * geçmişi görmek için ?pendingOnly=false.
     */
    @GetMapping("/claims")
    public ResponseEntity<List<RestaurantClaimResponse>> getClaims(
            @RequestParam(required = false, defaultValue = "true") boolean pendingOnly) {
        return ResponseEntity.ok(claimService.list(pendingOnly));
    }

    /**
     * PATCH /api/v1/admin/claims/{id}
     * Talebi sonuçlandırır: {"status": "APPROVED"} veya
     * {"status": "REJECTED", "adminNote": "..."}.
     */
    @PatchMapping("/claims/{id}")
    public ResponseEntity<RestaurantClaimResponse> reviewClaim(
            @PathVariable Long id,
            @Valid @RequestBody ReviewClaimRequest request) {
        return ResponseEntity.ok(
                claimService.review(id, request.getStatus(), request.getAdminNote()));
    }

    // ── Kullanıcı Yönetimi ────────────────────────────────────────────────────

    /** Tüm kullanıcıları listele */
    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    /** Kullanıcı rolünü değiştir (USER / RESTAURANT_OWNER / ADMIN) */
    @PatchMapping("/users/{userId}/role")
    public ResponseEntity<UserResponse> changeRole(
            @PathVariable Long userId,
            @RequestParam UserRole role) {
        return ResponseEntity.ok(adminService.changeUserRole(userId, role));
    }
}
