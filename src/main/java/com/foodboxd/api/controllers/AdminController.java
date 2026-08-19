package com.foodboxd.api.controllers;

import com.foodboxd.api.dtos.requests.RejectApplicationRequest;
import com.foodboxd.api.dtos.responses.RestaurantApplicationResponse;
import com.foodboxd.api.dtos.responses.UserResponse;
import com.foodboxd.api.entities.UserRole;
import com.foodboxd.api.services.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ── Restoran Başvuruları ──────────────────────────────────────────────────

    /** Tüm başvurular (durum filtresi opsiyonel: PENDING, APPROVED, REJECTED) */
    @GetMapping("/applications")
    public ResponseEntity<List<RestaurantApplicationResponse>> getApplications(
            @RequestParam(required = false, defaultValue = "false") boolean pendingOnly) {
        var result = pendingOnly
                ? adminService.getPendingApplications()
                : adminService.getAllApplications();
        return ResponseEntity.ok(result);
    }

    /** Başvuruyu onayla → restoran oluştur, kullanıcıyı RESTAURANT_OWNER yap */
    @PostMapping("/applications/{id}/approve")
    public ResponseEntity<RestaurantApplicationResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.approveApplication(id));
    }

    /** Başvuruyu reddet */
    @PostMapping("/applications/{id}/reject")
    public ResponseEntity<RestaurantApplicationResponse> reject(
            @PathVariable Long id,
            @RequestBody(required = false) RejectApplicationRequest request) {
        String note = request != null ? request.getAdminNote() : null;
        return ResponseEntity.ok(adminService.rejectApplication(id, note));
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
