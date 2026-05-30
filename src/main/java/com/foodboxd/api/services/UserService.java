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

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

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
    // Public: Entity → Response DTO (used by other services)
    // -----------------------------------------------------------------------
    public UserResponse toResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .bio(user.getBio())
                .build();
    }
}
