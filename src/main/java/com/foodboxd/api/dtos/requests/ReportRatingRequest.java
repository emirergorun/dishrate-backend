package com.foodboxd.api.dtos.requests;

import com.foodboxd.api.entities.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportRatingRequest {

    @NotNull(message = "Bildirim sebebi seçilmeli")
    private ReportReason reason;

    /** İsteğe bağlı açıklama; mobil yalnızca "Başka bir sebep"te gönderir. */
    @Size(max = 300, message = "Açıklama en fazla 300 karakter olabilir")
    private String note;
}
