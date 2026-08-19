package com.foodboxd.api.dtos.responses;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bir menü öğesine yapılan değerlendirme — gizlilik için değerlendirenin
 * adı maskelenir (örn. "E*** E***"). Kullanıcı kendi yorumunu gerçek adıyla görür.
 * Gerçek kullanıcı kimliği (userId/username) dışarı verilmez.
 */
@Getter
@Builder
public class MenuItemReviewResponse {

    private Long ratingId;
    private String reviewerName; // maskeli (başkası) veya gerçek (kendi yorumu)
    private boolean mine;
    private BigDecimal score;
    private String comment;
    private LocalDateTime ratedAt;
}
