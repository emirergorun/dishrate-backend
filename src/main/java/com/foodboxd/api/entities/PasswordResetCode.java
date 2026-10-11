package com.foodboxd.api.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * Şifre sıfırlama kodu (2.7). Kodun kendisi saklanmaz, karması
 * ({@code codeHash}) saklanır. Hesap silinince kodlar da silinir.
 */
@Entity
@Table(name = "password_reset_codes", indexes = @Index(columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    /** Kullanıldığı ya da geçersiz kılındığı an; null ise hâlâ geçerli olabilir. */
    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
