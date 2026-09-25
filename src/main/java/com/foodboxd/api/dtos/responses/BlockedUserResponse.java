package com.foodboxd.api.dtos.responses;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** Engellenenler listesindeki bir satır. Ad "@kullanıcıadı"; kimlik (id) dışarı verilmez. */
@Getter
@Builder
public class BlockedUserResponse {

    private Long blockId;
    private String name;
    private LocalDateTime blockedAt;
}
