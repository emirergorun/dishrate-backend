package com.foodboxd.api.dtos.responses;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** Admin: bir değerlendirmenin bildirim özeti (2.11a; moderasyon mailinin aynısı). */
@Getter
@Builder
public class RatingModerationResponse {

    private Long ratingId;
    private String author;
    private String dish;
    private String restaurant;
    private BigDecimal score;
    private String comment;
    private String photoUrl;
    private boolean hidden;
    private Instant contentRemovedAt;
    private List<Report> reports;

    @Getter
    @Builder
    public static class Report {
        private String reason;
        private String note;
        private String reporter;
        private LocalDateTime createdAt;
        private boolean resolved;
    }
}
