package com.foodboxd.api.dtos.responses;

import com.foodboxd.api.entities.AppNotification;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class NotificationResponse {

    private Long id;
    private String type;
    private String title;
    private String body;
    private Long menuItemId;
    private String menuItemName;
    private boolean read;
    private Instant createdAt;

    public static NotificationResponse from(AppNotification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .body(n.getBody())
                .menuItemId(n.getMenuItemId())
                .menuItemName(n.getMenuItemName())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
