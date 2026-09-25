package com.foodboxd.api.controllers;

import com.foodboxd.api.dtos.requests.ReportRatingRequest;
import com.foodboxd.api.dtos.responses.BlockedUserResponse;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.services.ModerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Bildirme ve engelleme (1.7). Hepsi giriş ister; işlem her zaman giriş yapan adına. */
@RestController
@RequiredArgsConstructor
public class ModerationController {

    private final ModerationService moderationService;

    /** POST /api/v1/ratings/{ratingId}/reports — değerlendirmeyi bildir. */
    @PostMapping("/ratings/{ratingId}/reports")
    public ResponseEntity<Void> report(
            @PathVariable Long ratingId,
            @Valid @RequestBody ReportRatingRequest request,
            @AuthenticationPrincipal User currentUser) {
        moderationService.report(currentUser, ratingId, request.getReason(), request.getNote());
        return ResponseEntity.noContent().build();
    }

    /** DELETE /api/v1/ratings/{ratingId}/reports — kendi bildirimini geri al. */
    @DeleteMapping("/ratings/{ratingId}/reports")
    public ResponseEntity<Void> undoReport(
            @PathVariable Long ratingId,
            @AuthenticationPrincipal User currentUser) {
        moderationService.undoReport(currentUser, ratingId);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/v1/ratings/{ratingId}/block-author — yorumu yazanı engelle. */
    @PostMapping("/ratings/{ratingId}/block-author")
    public ResponseEntity<Void> blockAuthor(
            @PathVariable Long ratingId,
            @AuthenticationPrincipal User currentUser) {
        moderationService.blockAuthorOf(currentUser, ratingId);
        return ResponseEntity.noContent().build();
    }

    /** DELETE /api/v1/ratings/{ratingId}/block-author — engeli geri al. */
    @DeleteMapping("/ratings/{ratingId}/block-author")
    public ResponseEntity<Void> unblockAuthor(
            @PathVariable Long ratingId,
            @AuthenticationPrincipal User currentUser) {
        moderationService.unblockAuthorOf(currentUser, ratingId);
        return ResponseEntity.noContent().build();
    }

    /** GET /api/v1/users/me/blocks — engellenenler, en yenisi önce. */
    @GetMapping("/users/me/blocks")
    public ResponseEntity<List<BlockedUserResponse>> blocks(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(moderationService.blocks(currentUser));
    }

    /** DELETE /api/v1/users/me/blocks/{blockId} — engeli kaldır. */
    @DeleteMapping("/users/me/blocks/{blockId}")
    public ResponseEntity<Void> unblock(
            @PathVariable Long blockId,
            @AuthenticationPrincipal User currentUser) {
        moderationService.unblock(currentUser, blockId);
        return ResponseEntity.noContent().build();
    }
}
