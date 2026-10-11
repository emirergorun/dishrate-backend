package com.foodboxd.api.config;

import com.foodboxd.api.entities.User;
import com.foodboxd.api.entities.UserRole;
import com.foodboxd.api.repositories.UserRepository;
import com.foodboxd.api.services.MemberCodeGenerator;
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
    private final MemberCodeGenerator memberCodeGenerator;

    @Value("${admin.seed.username:}")
    private String adminUsername;

    @Value("${admin.seed.email:}")
    private String adminEmail;

    @Value("${admin.seed.password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        // Sunucuda admin bilgisi ortam değişkeniyle bir kez verilir (2.13);
        // verilmemişse uygulama açılmaya devam eder, yalnız bu adım atlanır.
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("Admin bilgisi verilmedi (ADMIN_SEED_EMAIL / ADMIN_SEED_PASSWORD); admin oluşturma atlandı.");
            return;
        }
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Admin hesabı zaten mevcut: {}", adminEmail);
            return;
        }

        User admin = User.builder()
                .username(adminUsername)
                .firstName("Decouva")
                .lastName("Admin")
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .emailVerifiedAt(java.time.Instant.now())
                .role(UserRole.ADMIN)
                .memberCode(memberCodeGenerator.next())
                .build();

        userRepository.save(admin);
        log.info("Admin hesabı oluşturuldu: {}", adminEmail);
    }
}
