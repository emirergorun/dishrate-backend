package com.foodboxd.api.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Kullanıcı engelleme. Etkisi iki yönlü: iki taraf da birbirinin
 * değerlendirmelerini görmez (karar 24 Eylül). Yalnızca engelleyen kaldırabilir.
 *
 * <p>Yabancı anahtarlar {@code ON DELETE CASCADE}: hesaplardan biri silinince
 * engel de gider.
 */
@Entity
@Table(name = "user_blocks", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"blocker_id", "blocked_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "block_id")
    private Long blockId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User blocked;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
