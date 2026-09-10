package com.foodboxd.api.dtos.requests;

import com.foodboxd.api.entities.ClaimStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Admin'in bir sahiplik talebi hakkındaki kararı. */
@Getter
@Setter
public class ReviewClaimRequest {

    /** Yalnızca APPROVED veya REJECTED kabul edilir; servis doğrular. */
    @NotNull(message = "Karar (status) gerekli: APPROVED veya REJECTED")
    private ClaimStatus status;

    /** Red gerekçesi — reddederken doldurulması önerilir. */
    private String adminNote;
}
