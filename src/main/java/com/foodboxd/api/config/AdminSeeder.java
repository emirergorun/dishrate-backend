package com.foodboxd.api.config;

import com.foodboxd.api.entities.User;
import com.foodboxd.api.entities.UserRole;
import com.foodboxd.api.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.seed.username}")
    private String adminUsername;

    @Value("${admin.seed.email}")
    private String adminEmail;

    @Value("${admin.seed.password}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Admin hesabı zaten mevcut: {}", adminEmail);
            return;
        }

        User admin = User.builder()
                .username(adminUsername)
                .firstName("Dishrate")
                .lastName("Admin")
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(UserRole.ADMIN)
                .build();

        userRepository.save(admin);
        log.info("Admin hesabı oluşturuldu: {}", adminEmail);
    }
}
