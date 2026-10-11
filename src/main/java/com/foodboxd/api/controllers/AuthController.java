package com.foodboxd.api.controllers;

import com.foodboxd.api.dtos.requests.CreateUserRequest;
import com.foodboxd.api.dtos.requests.ForgotPasswordRequest;
import com.foodboxd.api.dtos.requests.ResetPasswordRequest;
import com.foodboxd.api.dtos.requests.LoginRequest;
import com.foodboxd.api.dtos.requests.RefreshTokenRequest;
import com.foodboxd.api.dtos.responses.AuthResponse;
import com.foodboxd.api.dtos.responses.UserResponse;
import com.foodboxd.api.services.AuthService;
import com.foodboxd.api.services.PasswordResetService;
import com.foodboxd.api.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final PasswordResetService passwordResetService;

    /**
     * POST /api/v1/auth/register
     * Yeni kullanıcı kaydı → access + refresh token döner
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = userService.createUser(request);

        // Kayıt sonrası otomatik login
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(request.getEmail());
        loginRequest.setPassword(request.getPassword());
        AuthResponse response = authService.login(loginRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/auth/login
     * Email + şifre → access + refresh token
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * POST /api/v1/auth/refresh
     * Refresh token → yeni access token
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    /**
     * POST /api/v1/auth/password/forgot — şifremi unuttum, kod iste (2.7).
     * Cevap hesabın var olup olmadığından bağımsız olarak hep aynı.
     */
    @PostMapping("/password/forgot")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestCode(request.getIdentifier());
        return ResponseEntity.ok(Map.of("message",
                "Bu bilgiyle kayıtlı bir hesap varsa e-postana 6 haneli bir kod gönderdik."));
    }

    /**
     * POST /api/v1/auth/password/reset — kod + yeni şifre (2.7). Başarıda
     * bütün oturumlar kapanır; istemci yeni şifreyle giriş yapar.
     */
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(
                request.getIdentifier(), request.getCode(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/auth/logout
     * Refresh token'ı geçersiz kılar
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
