package com.foodboxd.api.entities;

/** Bir değerlendirmenin neden bildirildiği (mobildeki sebep listesiyle aynı sıra ve ad). */
public enum ReportReason {
    SPAM("Spam ya da reklam"),
    HARASSMENT("Hakaret ya da nefret söylemi"),
    SEXUAL_OR_VIOLENT("Cinsel ya da şiddet içeren içerik"),
    OFF_TOPIC("Yemekle ilgisi yok"),
    PERSONAL_INFO("Kişisel bilgi paylaşılmış"),
    OTHER("Başka bir sebep");

    /** Moderasyon mailinde görünen ad (2.11a). */
    public final String label;

    ReportReason(String label) {
        this.label = label;
    }
}
