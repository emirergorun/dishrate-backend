package com.foodboxd.api.controllers;

import com.foodboxd.api.dtos.responses.NotificationResponse;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** GET /api/v1/notifications — giriş yapmış kullanıcının bildirimleri (en yeni önce). */
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> myNotifications(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(notificationService.getMyNotifications(currentUser));
    }

    /** GET /api/v1/notifications/unread-count — okunmamış bildirim sayısı. */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(currentUser)));
    }

    /** PATCH /api/v1/notifications/{id}/read — tek bildirimi okundu işaretle. */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        notificationService.markRead(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    /** PATCH /api/v1/notifications/read-all — hepsini okundu işaretle. */
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal User currentUser) {
        notificationService.markAllRead(currentUser);
        return ResponseEntity.noContent().build();
    }
}
