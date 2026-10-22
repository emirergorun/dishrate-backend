package com.foodboxd.api.controllers;

import com.foodboxd.api.dtos.requests.ReviewClaimRequest;
import com.foodboxd.api.dtos.responses.RatingModerationResponse;
import com.foodboxd.api.dtos.responses.RestaurantClaimResponse;
import com.foodboxd.api.dtos.responses.UserResponse;
import com.foodboxd.api.entities.UserRole;
import com.foodboxd.api.services.AdminService;
import com.foodboxd.api.services.ClaimService;
import com.foodboxd.api.services.ModerationService;
import com.foodboxd.api.services.importer.CatalogImportReport;
import com.foodboxd.api.services.importer.CatalogImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ClaimService claimService;
    private final ModerationService moderationService;
    private final CatalogImportService catalogImportService;

    // ── İçerik içe aktarma (2.11b) ────────────────────────────────────────────

    /**
     * POST /api/v1/admin/import/catalog
     * Restoranları, menülerini ve fotoğraflarını tablodan (CSV) aktarır;
     * fotoğraflar isteğe bağlı zip'te, tablodaki dosya adıyla. Varsayılan
     * önizleme ({@code dryRun=true}): hiçbir şey yazılmaz, rapor döner.
     * {@code dryRun=false} ile yazılır; tek bir hata bile varsa hiçbiri.
     * Kurallar {@code CatalogImportService}'te.
     */
    @PostMapping(value = "/import/catalog", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CatalogImportReport> importCatalog(
            @RequestPart("csv") MultipartFile csv,
            @RequestPart(value = "photos", required = false) MultipartFile photos,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        String base = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
        return ResponseEntity.ok(catalogImportService.run(csv, photos, dryRun, base));
    }

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

    // ── Bildirilen yorumlar (2.11a) ───────────────────────────────────────────

    /** GET /api/v1/admin/ratings/{id}/reports — değerlendirme ve bildirimleri. */
    @GetMapping("/ratings/{id}/reports")
    public ResponseEntity<RatingModerationResponse> ratingReports(@PathVariable Long id) {
        return ResponseEntity.ok(moderationService.reportsOf(id));
    }

    /**
     * POST /api/v1/admin/ratings/{id}/remove-content — yorum ve fotoğraf
     * silinir, puan kalır; bildirimler çözülür.
     */
    @PostMapping("/ratings/{id}/remove-content")
    public ResponseEntity<RatingModerationResponse> removeContent(@PathVariable Long id) {
        return ResponseEntity.ok(moderationService.removeContent(id));
    }

    /**
     * POST /api/v1/admin/ratings/{id}/dismiss-reports — bildirimler yok
     * sayılır, değerlendirme yeniden görünür.
     */
    @PostMapping("/ratings/{id}/dismiss-reports")
    public ResponseEntity<RatingModerationResponse> dismissReports(@PathVariable Long id) {
        return ResponseEntity.ok(moderationService.dismissReports(id));
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
