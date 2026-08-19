package com.foodboxd.api.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "restaurant_applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Başvuruyu yapan kullanıcı
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private User applicant;

    // Restoran bilgileri
    @Column(name = "restaurant_name", nullable = false, length = 255)
    private String restaurantName;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "full_address", columnDefinition = "TEXT")
    private String fullAddress;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    // Başvuru açıklaması (opsiyonel)
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Durum
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.PENDING;

    // Admin notu (red sebebi vb.)
    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    // ── Sahiplik talebi (claim) alanları ─────────────────────────────────────
    // Dolu ise: mevcut bir restoranın sahiplik talebi
    // Boş ise: yeni restoran açma başvurusu
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_restaurant_id", nullable = true)
    private Restaurant targetRestaurant;

    // Onay sonrası bağlanan restoran (yeni açılış veya claim — her ikisi de buraya yazılır)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_restaurant_id", nullable = true)
    private Restaurant linkedRestaurant;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
