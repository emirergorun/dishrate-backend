package com.foodboxd.api.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "co_owner_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoOwnerRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ortak olmak isteyen kişi
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private User applicant;

    // Hangi restoran için başvuruyor
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    // Başvuru durumu
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.PENDING;

    // Onaylayan veya reddeden PRIMARY_OWNER
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id", nullable = true)
    private User reviewedBy;

    // Red sebebi (opsiyonel)
    @Column(name = "rejection_note", columnDefinition = "TEXT")
    private String rejectionNote;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
