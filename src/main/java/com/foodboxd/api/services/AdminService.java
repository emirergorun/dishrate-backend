package com.foodboxd.api.services;

import com.foodboxd.api.dtos.responses.UserResponse;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.entities.UserRole;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Admin işlemleri — kullanıcı yönetimi.
 *
 * <p>Sahiplik talepleri {@link ClaimService} içinde; admin uçları oradan
 * beslenir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(userService::toResponse)
                .toList();
    }

    @Transactional
    public UserResponse changeUserRole(Long userId, UserRole newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı. ID: " + userId));
        user.setRole(newRole);
        userRepository.save(user);
        log.info("Kullanıcı rolü değiştirildi. ID: {}, Yeni rol: {}", userId, newRole);
        return userService.toResponse(user);
    }
}
