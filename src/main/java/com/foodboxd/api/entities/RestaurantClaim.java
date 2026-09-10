package com.foodboxd.api.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Bir kullanıcının mevcut bir restoranın sahibi olduğuna dair talebi.
 *
 * <p>Önceki "başvuru" modelinden farkı: burada restoran zaten katalogda
 * kayıtlıdır. Talep, var olan bir kaydın sahipliğini istemektir — yeni
 * restoran oluşturmaz.
 *
 * <p>Aynı kullanıcı aynı restoran için birden fazla kez talep açamaz
 * (benzersizlik kısıtı). Reddedilen bir talep silinmez; kayıt olarak kalır.
 */
@Entity
@Table(
        name = "restaurant_claims",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_claim_user_restaurant",
                columnNames = {"user_id", "restaurant_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Talebi açan kullanıcı. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Sahipliği istenen restoran. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ClaimStatus status = ClaimStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /** Admin kararını verdiği an. Karar verilmemişse null. */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** Red gerekçesi — kullanıcı neden reddedildiğini görebilsin diye. */
    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;
}
