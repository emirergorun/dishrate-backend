package com.foodboxd.api.dtos.responses;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Keşfet ekranının tek bir bölümü.
 *
 * <p>Başlık ve alt başlık burada YOK: onlar arayüz metni ve istemcide duruyor.
 * Sunucu yalnızca bölümün kimliğini ve içeriğini verir; böylece metin
 * değişikliği sunucu sürümüne bağlı olmaz.
 */
@Getter
@Builder
public class FeedSectionResponse {

    /** İstemcinin başlıkla eşleştirdiği anahtar: "top-rated", "weekly"… */
    private String key;

    private List<MenuItemResponse> items;

    /** Daha fazlası var mı — "Tümünü gör" bağlantısı buna göre gösterilir. */
    private boolean hasMore;

    /**
     * İlçe süzgeci uygulandı mı. İlçe seçiliyken {@code false} ise ilçede
     * yeterli içerik yoktu, il geneli döndü; istemci başlığı il adıyla yazar
     * ve bunu söyler. İlçe seçilmemişse de {@code false}.
     */
    private boolean districtApplied;
}
