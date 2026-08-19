package com.foodboxd.api.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Uygulama içi bildirim. Şimdilik owner'lara "ürününe yeni değerlendirme geldi"
 * bildirimi için kullanılıyor; FCM push eklendiğinde aynı kayıtlar push'a da temel olur.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    // NEW_RATING gibi tür etiketi (ileride başka türler eklenebilir)
    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    // İlgili menü öğesi (bildirime tıklayınca yorumlar açılır)
    @Column(name = "menu_item_id")
    private Long menuItemId;

    @Column(name = "menu_item_name", length = 255)
    private String menuItemName;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
