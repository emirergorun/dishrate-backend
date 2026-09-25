package com.foodboxd.api.dtos.responses;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bir yemeğe yapılan değerlendirme. Yazar "@kullanıcıadı" olarak görünür
 * (karar 25 Eylül); ad, soyad ve kullanıcı kimliği (id) dışarı verilmez.
 */
@Getter
@Builder
public class MenuItemReviewResponse {

    private Long ratingId;
    private String reviewerName; // "@kullaniciadi"
    private boolean mine;
    private BigDecimal score;
    private String comment;
    private String photoUrl;
    private LocalDateTime ratedAt;
}
