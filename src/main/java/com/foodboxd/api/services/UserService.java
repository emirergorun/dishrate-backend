package com.foodboxd.api.services;

import com.foodboxd.api.dtos.requests.CreateUserRequest;
import com.foodboxd.api.dtos.requests.UpdateUserRequest;
import com.foodboxd.api.dtos.responses.UserResponse;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.exceptions.ResourceAlreadyExistsException;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    /** İsim/soyisim bu kadar günde bir kez değiştirilebilir. */
    private static final int NAME_CHANGE_COOLDOWN_DAYS = 15;
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // -----------------------------------------------------------------------
    // Create a new user
    // -----------------------------------------------------------------------
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        log.info("Create user request received. Username: {}", request.getUsername());

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResourceAlreadyExistsException(
                    "Username already taken: " + request.getUsername()
            );
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResourceAlreadyExistsException(
                    "Email already registered: " + request.getEmail()
            );
        }

        User user = User.builder()
                .username(request.getUsername())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .profilePhotoUrl(request.getProfilePhotoUrl())
                .bio(request.getBio())
                .build();

        User saved = userRepository.save(user);
        log.info("User created successfully. ID: {}", saved.getUserId());
        return toResponse(saved);
    }

    // -----------------------------------------------------------------------
    // Get user by ID
    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        log.debug("Fetching user. ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found. ID: " + userId
                ));
        return toResponse(user);
    }

    // -----------------------------------------------------------------------
    // Get all users
    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        log.debug("Fetching all users.");
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Update user profile
    // -----------------------------------------------------------------------
    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        log.info("Update user request. ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found for update. ID: " + userId
                ));

        // Kullanıcı adı — benzersiz olmalı
        if (request.getUsername() != null
                && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new ResourceAlreadyExistsException(
                        "Username already taken: " + request.getUsername());
            }
            user.setUsername(request.getUsername());
        }

        // İsim / soyisim — 15 günde bir kez değiştirilebilir
        String newFirst = request.getFirstName();
        String newLast = request.getLastName();
        boolean firstChanging = newFirst != null && !newFirst.isBlank()
                && !newFirst.trim().equals(user.getFirstName());
        boolean lastChanging = newLast != null && !newLast.isBlank()
                && !newLast.trim().equals(user.getLastName());

        if (firstChanging || lastChanging) {
            LocalDateTime now = LocalDateTime.now();
            if (user.getNameLastChangedAt() != null) {
                LocalDateTime nextAllowed =
                        user.getNameLastChangedAt().plusDays(NAME_CHANGE_COOLDOWN_DAYS);
                if (now.isBefore(nextAllowed)) {
                    throw new IllegalStateException(
                            "İsim ve soyisim 15 günde bir değiştirilebilir. "
                                    + "Tekrar değiştirebileceğin tarih: "
                                    + nextAllowed.format(DATE_FMT));
                }
            }
            if (firstChanging) user.setFirstName(newFirst.trim());
            if (lastChanging) user.setLastName(newLast.trim());
            user.setNameLastChangedAt(now);
        }

        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        if (request.getProfilePhotoUrl() != null) {
            user.setProfilePhotoUrl(request.getProfilePhotoUrl());
        }

        User saved = userRepository.save(user);
        log.info("User updated successfully. ID: {}", userId);
        return toResponse(saved);
    }

    // -----------------------------------------------------------------------
    // Delete a user
    // -----------------------------------------------------------------------
    @Transactional
    public void deleteUser(Long userId) {
        log.info("Delete user request. ID: {}", userId);
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException(
                    "User not found for deletion. ID: " + userId
            );
        }
        userRepository.deleteById(userId);
        log.info("User deleted successfully. ID: {}", userId);
    }

    // -----------------------------------------------------------------------
    // Change password
    // -----------------------------------------------------------------------
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        log.info("Change password request. ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found. ID: " + userId
                ));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            // 401 yerine 409: DioClient'in 401 → token-refresh interceptor'ını tetiklememek için
            throw new IllegalStateException("Mevcut şifre hatalı.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalStateException("Yeni şifre eskisiyle aynı olamaz.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed successfully. ID: {}", userId);
    }

    // -----------------------------------------------------------------------
    // Public: Entity → Response DTO (used by other services)
    // -----------------------------------------------------------------------
    public UserResponse toResponse(User user) {
        // İsim/soyisim'in tekrar değiştirilebileceği zaman — pencere dolduysa null.
        LocalDateTime nameChangeAvailableAt = null;
        if (user.getNameLastChangedAt() != null) {
            LocalDateTime nextAllowed =
                    user.getNameLastChangedAt().plusDays(NAME_CHANGE_COOLDOWN_DAYS);
            if (LocalDateTime.now().isBefore(nextAllowed)) {
                nameChangeAvailableAt = nextAllowed;
            }
        }

        return UserResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .bio(user.getBio())
                .role(user.getRole())
                .nameChangeAvailableAt(nameChangeAvailableAt)
                .build();
    }
}
