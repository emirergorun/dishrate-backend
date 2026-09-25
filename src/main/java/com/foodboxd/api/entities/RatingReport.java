package com.foodboxd.api.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Bir kullanıcının bir değerlendirmeyi (yorum/fotoğraf) bildirmesi.
 *
 * <p>İnceleme web panelinde yapılacak (yol haritası 2.11). O zamana kadar
 * aynı değerlendirmeyi {@link com.foodboxd.api.services.ModerationService#HIDE_THRESHOLD}
 * farklı kişi bildirince değerlendirme {@link Rating#isHidden() gizlenir}.
 *
 * <p>Yabancı anahtarlar veritabanında {@code ON DELETE CASCADE}: değerlendirme
 * ya da hesap silinince bildirimler de gider, silme kodlarına ek iş düşmez.
 */
@Entity
@Table(name = "rating_reports", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"rating_id", "reporter_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RatingReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rating_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Rating rating;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 32)
    private ReportReason reason;

    /**
     * "Başka bir sebep" seçilince isteğe bağlı kısa açıklama. Yalnızca
     * incelemede (2.11) görünür; yorumun yazarı görmez.
     */
    @Column(name = "note", length = 300)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
