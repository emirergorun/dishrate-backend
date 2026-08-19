package com.foodboxd.api.controllers;

import com.foodboxd.api.dtos.requests.CreateRestaurantApplicationRequest;
import com.foodboxd.api.dtos.responses.RestaurantApplicationResponse;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.services.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    /**
     * POST /api/v1/applications
     * Giriş yapmış herhangi bir kullanıcı yeni restoran başvurusu yapabilir.
     */
    @PostMapping
    public ResponseEntity<RestaurantApplicationResponse> submit(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateRestaurantApplicationRequest request) {
        RestaurantApplicationResponse response = applicationService.submit(currentUser, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/v1/applications/me
     * Giriş yapmış kullanıcının kendi başvuruları (durum takibi için).
     */
    @GetMapping("/me")
    public ResponseEntity<List<RestaurantApplicationResponse>> myApplications(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(applicationService.getMyApplications(currentUser));
    }
}
