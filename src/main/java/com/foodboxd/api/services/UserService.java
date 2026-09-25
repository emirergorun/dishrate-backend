package com.foodboxd.api.services;

import com.foodboxd.api.dtos.requests.CreateUserRequest;
import com.foodboxd.api.dtos.requests.UpdateUserRequest;
import com.foodboxd.api.dtos.responses.UserResponse;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.exceptions.ResourceAlreadyExistsException;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.repositories.UserRepository;
import com.foodboxd.api.utils.ProfanityFilter;
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
    private final ProfanityFilter profanityFilter;

    // -----------------------------------------------------------------------
    // Create a new user
    // -----------------------------------------------------------------------
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        // Kullanıcı adı ve e-posta normalize edilir (kırp + küçük harf).
        // Böylece "Emir_Test" ile "emir_test" AYNI hesap sayılır — hem giriş
        // kolaylaşır hem de benzer isimle taklit hesap açılması engellenir.
        final String username = normalize(request.getUsername());
        final String email = normalize(request.getEmail());
        log.info("Create user request received. Username: {}", username);
        requireCleanUsername(username);
        requireCleanName(request.getFirstName(), request.getLastName());

        if (userRepository.existsByUsername(username)) {
            throw new ResourceAlreadyExistsException(
                    "Bu kullanıcı adı zaten kullanımda."
            );
        }
        if (userRepository.existsByEmail(email)) {
            throw new ResourceAlreadyExistsException(
                    "Bu e-posta zaten kayıtlı."
            );
        }

        User user = User.builder()
                .username(username)
                .firstName(trimOrNull(request.getFirstName()))
                .lastName(trimOrNull(request.getLastName()))
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .profilePhotoUrl(request.getProfilePhotoUrl())
                .bio(request.getBio())
                .build();

        User saved = userRepository.save(user);
        log.info("User created successfully. ID: {}", saved.getUserId());
        return toResponse(saved);
    }

    /** Kırpar ve küçük harfe çevirir — kullanıcı adı/e-posta için. */
    public static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String trimOrNull(String value) {
        if (value == null) return null;
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // -----------------------------------------------------------------------
    // Get user by ID
    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        log.debug("Fetching user. ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Kullanıcı bulunamadı."
                ));
        return toResponse(user);
    }

    // -----------------------------------------------------------------------
    // Update user profile
    // -----------------------------------------------------------------------
    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        log.info("Update user request. ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Kullanıcı bulunamadı."
                ));

        // Kullanıcı adı — benzersiz olmalı
        if (request.getUsername() != null) {
            final String newUsername = normalize(request.getUsername());
            if (!newUsername.equals(user.getUsername())) {
                requireCleanUsername(newUsername);
                if (userRepository.existsByUsername(newUsername)) {
                    throw new ResourceAlreadyExistsException(
                            "Bu kullanıcı adı zaten kullanımda.");
                }
                user.setUsername(newUsername);
            }
        }

        // İsim / soyisim — 15 günde bir kez değiştirilebilir
        String newFirst = request.getFirstName();
        String newLast = request.getLastName();
        boolean firstChanging = newFirst != null && !newFirst.isBlank()
                && !newFirst.trim().equals(user.getFirstName());
        boolean lastChanging = newLast != null && !newLast.isBlank()
                && !newLast.trim().equals(user.getLastName());

        if (firstChanging || lastChanging) {
            // Süzgeç 15 gün kuralından önce: reddedilen deneme hakkı yemesin.
            requireCleanName(firstChanging ? newFirst : user.getFirstName(),
                    lastChanging ? newLast : user.getLastName());
            LocalDateTime now = LocalDateTime.now();
            if (user.getNameLastChangedAt() != null) {
                LocalDateTime nextAllowed =
                        user.getNameLastChangedAt().plusDays(NAME_CHANGE_COOLDOWN_DAYS);
                if (now.isBefore(nextAllowed)) {
                    throw new IllegalStateException(
                            "Ad ve soyad 15 günde bir değiştirilebilir. "
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
        // Fotoğraf kaldırıldığında üç alan da boş metin olarak geliyor.
        // trimOrNull sayesinde veritabanına boş metin değil null yazılır;
        // aksi hâlde istemci "fotoğraf var" sanıp kırık görsel gösteriyordu.
        if (request.getProfilePhotoUrl() != null) {
            user.setProfilePhotoUrl(trimOrNull(request.getProfilePhotoUrl()));
        }
        if (request.getProfilePhotoOriginalUrl() != null) {
            user.setProfilePhotoOriginalUrl(trimOrNull(request.getProfilePhotoOriginalUrl()));
        }
        if (request.getProfilePhotoCrop() != null) {
            user.setProfilePhotoCrop(trimOrNull(request.getProfilePhotoCrop()));
        }

        User saved = userRepository.save(user);
        log.info("User updated successfully. ID: {}", userId);
        return toResponse(saved);
    }

    // -----------------------------------------------------------------------
    // Change password
    // -----------------------------------------------------------------------
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        log.info("Change password request. ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Kullanıcı bulunamadı."
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
                .profilePhotoOriginalUrl(user.getProfilePhotoOriginalUrl())
                .profilePhotoCrop(user.getProfilePhotoCrop())
                .bio(user.getBio())
                .role(user.getRole())
                .nameChangeAvailableAt(nameChangeAvailableAt)
                .build();
    }

    /**
     * Kullanıcı adı yorumlarda herkese görünüyor (karar 25 Eylül); küfürlü
     * ad yorum süzgecinden geçmeden her yorumun başında dururdu. Ad "_" ve
     * "." ile bölünmüş parçalar olarak da denetlenir ("xx_kufur").
     */
    /**
     * Ad ve soyad bugün başkalarına görünmüyor (yorumlarda kullanıcı adı var),
     * ama profil 6.2'de açılınca görünecek; uygunsuz ad baştan girmesin.
     * Ayrı ayrı ve birlikte denetlenir: iki kelimelik kalıplar ("ananın amı")
     * ad ile soyadın arasına bölünebiliyor.
     */
    private void requireCleanName(String first, String last) {
        String f = first == null ? "" : first;
        String l = last == null ? "" : last;
        if (profanityFilter.containsProfanity(f)
                || profanityFilter.containsProfanity(l)
                || profanityFilter.containsProfanity(f + " " + l)) {
            throw new IllegalArgumentException("Ad ya da soyad kullanılamaz, başka bir ad yaz.");
        }
    }

    private void requireCleanUsername(String username) {
        if (profanityFilter.containsProfanity(username.replaceAll("[._-]", " "))
                || profanityFilter.containsProfanity(username)) {
            throw new IllegalArgumentException("Bu kullanıcı adı kullanılamaz, başka bir ad seç.");
        }
    }
}
