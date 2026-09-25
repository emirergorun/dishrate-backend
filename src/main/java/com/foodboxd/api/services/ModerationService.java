package com.foodboxd.api.services;

import com.foodboxd.api.dtos.responses.BlockedUserResponse;
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

import java.util.List;

/**
 * Kullanıcı içeriği denetimi (1.7): değerlendirme bildirme ve kullanıcı
 * engelleme. İnceleme ekranı web panelinde gelecek (2.11).
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
        long count = reportRepository.countByRating_RatingId(ratingId);
        log.info("Rating reported. Rating ID: {}, reason: {}, total reports: {}",
                ratingId, reason, count);
        if (count >= HIDE_THRESHOLD && !rating.isHidden()) {
            rating.setHidden(true);
            log.warn("Rating hidden after {} reports. Rating ID: {}", count, ratingId);
        }
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
     * gizlendiyse ve sayı eşiğin altına indiyse yeniden görünür. 2.11'de
     * panelden elle gizleme gelirse bu kural onu da açmamalı; o zaman ayrı bir
     * alan gerekir.
     */
    @Transactional
    public void undoReport(User viewer, Long ratingId) {
        Rating rating = findRating(ratingId);
        reportRepository.deleteByRating_RatingIdAndReporter_UserId(ratingId, viewer.getUserId());
        if (rating.isHidden()
                && reportRepository.countByRating_RatingId(ratingId) < HIDE_THRESHOLD) {
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
