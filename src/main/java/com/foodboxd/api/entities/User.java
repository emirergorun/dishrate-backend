package com.foodboxd.api.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "username"),
        @UniqueConstraint(columnNames = "email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "first_name", length = 50)
    private String firstName;

    @Column(name = "last_name", length = 50)
    private String lastName;

    // İsim/soyisim en son ne zaman değiştirildi (15 günde bir kuralı için).
    @Column(name = "name_last_changed_at")
    private LocalDateTime nameLastChangedAt;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, columnDefinition = "TEXT")
    private String passwordHash;

    /** Kırpılmış, uygulamada gösterilen hâli. */
    @Column(name = "profile_photo_url", columnDefinition = "TEXT")
    private String profilePhotoUrl;

    /**
     * Kırpılmamış özgün yükleme. Kullanıcı fotoğrafını yeniden çerçevelemek
     * istediğinde galeriden yeniden seçmek zorunda kalmasın diye saklanır;
     * kırpılmışı tekrar kırpmak kaliteyi her seferinde düşürürdü.
     */
    @Column(name = "profile_photo_original_url", columnDefinition = "TEXT")
    private String profilePhotoOriginalUrl;

    /**
     * Özgün görsel üzerindeki kırpma dikdörtgeni: "x,y,genişlik,yükseklik".
     * Düzenleme ekranı açıldığında önceki çerçeveleme aynen geri yüklenir.
     */
    @Column(name = "profile_photo_crop", length = 80)
    private String profilePhotoCrop;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Rating> ratings;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<WishlistItem> wishlistItems;
}
