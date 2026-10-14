package com.foodboxd.api.services;

import com.foodboxd.api.dtos.responses.BlockedUserResponse;
import com.foodboxd.api.dtos.responses.RatingModerationResponse;
import com.foodboxd.api.entities.Rating;
import com.foodboxd.api.entities.RatingReport;
import com.foodboxd.api.entities.ReportReason;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.entities.UserBlock;
import com.foodboxd.api.exceptions.ResourceNotFoundException;
import com.foodboxd.api.repositories.RatingReportRepository;
import com.foodboxd.api.repositories.RatingRepository;
import com.foodboxd.api.repositories.UserBlockRepository;
import com.foodboxd.api.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Kullanıcı içeriği denetimi (1.7): değerlendirme bildirme ve kullanıcı
 * engelleme. İnceleme şimdilik e-postayla ve admin uçlarıyla (2.11a);
 * web paneli 6.7'de.
 *
 * <p>Yorumcunun kimliği dışarı verilmediği için engelleme değerlendirme
 * üzerinden yapılır: "bu yorumu yazanı engelle".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationService {

    /**
     * Bu kadar farklı kişi bildirince değerlendirme herkesten gizlenir.
     * İnceleme ekranı gelene kadar Apple'ın "uygunsuz içeriğe çabuk müdahale"
     * şartını bu eşik karşılıyor (karar 24 Eylül).
     */
    public static final int HIDE_THRESHOLD = 3;

    private final RatingRepository ratingRepository;
    private final RatingReportRepository reportRepository;
    private final UserBlockRepository blockRepository;
    private final UserRepository userRepository;
    private final ModerationNotifier notifier;
    private final FileStorageService fileStorageService;

    @Transactional
    public void report(User viewer, Long ratingId, ReportReason reason, String note) {
        Rating rating = findRating(ratingId);
        if (rating.getUser().getUserId().equals(viewer.getUserId())) {
            throw new IllegalArgumentException("Kendi değerlendirmeni bildiremezsin.");
        }
        // Aynı kişinin ikinci bildirimi sayılmaz; sessizce kabul edilir.
        if (reportRepository.existsByRating_RatingIdAndReporter_UserId(ratingId, viewer.getUserId())) {
            return;
        }
        reportRepository.save(RatingReport.builder()
                .rating(rating)
                .reporter(userRepository.getReferenceById(viewer.getUserId()))
                .reason(reason)
                .note(note == null || note.isBlank() ? null : note.strip())
                .build());
        // Eşik ve mail yalnız çözülmemiş bildirimleri sayar (2.11a): yok
        // sayılan bildirimler değerlendirmeyi yeniden gizlemez.
        long open = reportRepository.countByRating_RatingIdAndResolvedAtIsNull(ratingId);
        log.info("Rating reported. Rating ID: {}, reason: {}, open reports: {}",
                ratingId, reason, open);
        boolean crossed = open >= HIDE_THRESHOLD && !rating.isHidden();
        if (crossed) {
            rating.setHidden(true);
            log.warn("Rating hidden after {} reports. Rating ID: {}", open, ratingId);
        }
        // Destek kutusuna mail: ilk bildirimde ve gizlenince (en fazla iki).
        if (open == 1) {
            notifier.firstReport(summary(rating));
        } else if (crossed) {
            notifier.hidden(summary(rating), HIDE_THRESHOLD);
        }
    }

    // ── Admin (2.11a): bildirim incelemesi ────────────────────────────────

    @Transactional(readOnly = true)
    public RatingModerationResponse reportsOf(Long ratingId) {
        return summary(findRating(ratingId));
    }

    /** Yorum ve fotoğraf kaldırılır, puan kalır; açık bildirimler çözülür. */
    @Transactional
    public RatingModerationResponse removeContent(Long ratingId) {
        Rating rating = findRating(ratingId);
        String photo = rating.getPhotoUrl();
        Instant now = Instant.now();
        reportRepository.resolveAll(ratingId, now);
        ratingRepository.removeContent(ratingId, now);
        fileStorageService.deleteByUrl(photo);
        log.warn("Moderation: content removed. Rating ID: {}", ratingId);
        return summary(findRating(ratingId));
    }

    /** Bildirimler yok sayılır; bildirimlerle gizlendiyse yeniden görünür. */
    @Transactional
    public RatingModerationResponse dismissReports(Long ratingId) {
        findRating(ratingId);
        reportRepository.resolveAll(ratingId, Instant.now());
        ratingRepository.unhide(ratingId);
        log.info("Moderation: reports dismissed. Rating ID: {}", ratingId);
        return summary(findRating(ratingId));
    }

    private RatingModerationResponse summary(Rating rating) {
        return RatingModerationResponse.builder()
                .ratingId(rating.getRatingId())
                .author(rating.getUser().getUsername())
                .dish(rating.getMenuItem().getName())
                .restaurant(rating.getMenuItem().getRestaurant().getName())
                .score(rating.getScore())
                .comment(rating.getComment())
                .photoUrl(rating.getPhotoUrl())
                .hidden(rating.isHidden())
                .contentRemovedAt(rating.getContentRemovedAt())
                .reports(reportRepository.findAllByRating(rating.getRatingId()).stream()
                        .map(r -> RatingModerationResponse.Report.builder()
                                .reason(r.getReason().label)
                                .note(r.getNote())
                                .reporter(r.getReporter().getUsername())
                                .createdAt(r.getCreatedAt())
                                .resolved(r.getResolvedAt() != null)
                                .build())
                        .toList())
                .build();
    }

    @Transactional
    public void blockAuthorOf(User viewer, Long ratingId) {
        User author = findRating(ratingId).getUser();
        if (author.getUserId().equals(viewer.getUserId())) {
            throw new IllegalArgumentException("Kendini engelleyemezsin.");
        }
        if (blockRepository.existsByBlocker_UserIdAndBlocked_UserId(
                viewer.getUserId(), author.getUserId())) {
            return;
        }
        blockRepository.save(UserBlock.builder()
                .blocker(userRepository.getReferenceById(viewer.getUserId()))
                .blocked(author)
                .build());
        log.info("User blocked. Blocker ID: {}, blocked ID: {}",
                viewer.getUserId(), author.getUserId());
    }

    @Transactional(readOnly = true)
    public List<BlockedUserResponse> blocks(User viewer) {
        return blockRepository.findByBlocker(viewer.getUserId()).stream()
                .map(b -> BlockedUserResponse.builder()
                        .blockId(b.getBlockId())
                        .name("@" + b.getBlocked().getUsername())
                        .blockedAt(b.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public void unblock(User viewer, Long blockId) {
        UserBlock block = blockRepository.findById(blockId)
                .orElseThrow(() -> new ResourceNotFoundException("Engel kaydı bulunamadı."));
        if (!block.getBlocker().getUserId().equals(viewer.getUserId())) {
            throw new AccessDeniedException("Bu engeli kaldırma yetkin yok.");
        }
        blockRepository.delete(block);
    }

    /**
     * Bildirimi geri al (şeritteki "Geri al"). Değerlendirme bildirimlerle
     * gizlendiyse ve açık bildirim sayısı eşiğin altına indiyse yeniden
     * görünür. Moderasyonla kaldırılan içerik ({@code contentRemovedAt}) zaten
     * boş olduğundan bu kural ona dokunmaz.
     */
    @Transactional
    public void undoReport(User viewer, Long ratingId) {
        Rating rating = findRating(ratingId);
        reportRepository.deleteByRating_RatingIdAndReporter_UserId(ratingId, viewer.getUserId());
        if (rating.isHidden()
                && reportRepository.countByRating_RatingIdAndResolvedAtIsNull(ratingId) < HIDE_THRESHOLD) {
            rating.setHidden(false);
        }
    }

    /** Engeli geri al (şeritteki "Geri al"): kimlik yine değerlendirmeden. */
    @Transactional
    public void unblockAuthorOf(User viewer, Long ratingId) {
        User author = findRating(ratingId).getUser();
        blockRepository.deleteByBlocker_UserIdAndBlocked_UserId(
                viewer.getUserId(), author.getUserId());
    }

    private Rating findRating(Long ratingId) {
        return ratingRepository.findById(ratingId)
                .orElseThrow(() -> new ResourceNotFoundException("Değerlendirme bulunamadı."));
    }
}
