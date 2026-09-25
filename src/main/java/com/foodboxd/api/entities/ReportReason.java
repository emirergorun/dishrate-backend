package com.foodboxd.api.entities;

/** Bir değerlendirmenin neden bildirildiği (mobildeki sebep listesiyle aynı sıra). */
public enum ReportReason {
    SPAM,
    HARASSMENT,
    SEXUAL_OR_VIOLENT,
    OFF_TOPIC,
    PERSONAL_INFO,
    OTHER
}
