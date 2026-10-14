package com.foodboxd.api.services;

import com.foodboxd.api.dtos.responses.RatingModerationResponse;
import com.foodboxd.api.services.mail.MailService;
import com.foodboxd.api.services.mail.MailTemplates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Bildirilen değerlendirme için moderasyon maili (2.11a). İlk bildirimde ve
 * gizlenme eşiğinde bir kez gider; alıcı destek kutusu. Mail hatası bildirme
 * isteğini bozmaz.
 */
@Slf4j
@Component
public class ModerationNotifier {

    private final MailService mailService;
    private final String notifyTo;

    public ModerationNotifier(MailService mailService,
                              @Value("${app.moderation.notify-to}") String notifyTo) {
        this.mailService = mailService;
        this.notifyTo = notifyTo;
    }

    public void firstReport(RatingModerationResponse r) {
        send("Bildirilen yorum: " + r.getDish() + " · " + r.getRestaurant(),
                "Bir yorum bildirildi", r);
    }

    public void hidden(RatingModerationResponse r, int threshold) {
        send("Gizlendi (" + threshold + " bildirim): " + r.getDish() + " · " + r.getRestaurant(),
                "Yorum bildirimlerle gizlendi", r);
    }

    private void send(String subject, String heading, RatingModerationResponse r) {
        List<String> p = new ArrayList<>();
        p.add("Değerlendirme #" + r.getRatingId() + " · " + r.getDish() + " · " + r.getRestaurant());
        p.add("Yazar: @" + r.getAuthor() + " · Puan: " + r.getScore());
        p.add("Yorum: " + (r.getComment() == null || r.getComment().isBlank()
                ? "(yorum yok)" : "“" + r.getComment() + "”"));
        if (r.getPhotoUrl() != null && !r.getPhotoUrl().isBlank()) {
            p.add("Fotoğraf: " + r.getPhotoUrl());
        }
        List<RatingModerationResponse.Report> open = r.getReports().stream()
                .filter(x -> !x.isResolved()).toList();
        p.add("Durum: " + (r.isHidden() ? "herkesten gizli" : "yalnız bildirenlere gizli")
                + " · " + open.size() + " açık bildirim");
        for (RatingModerationResponse.Report x : open) {
            p.add("• " + x.getReason() + " — @" + x.getReporter()
                    + (x.getNote() == null ? "" : ": “" + x.getNote() + "”"));
        }
        p.add("Ne yapabilirsin (Swagger'da admin hesabıyla, numara " + r.getRatingId() + "):");
        p.add("İçeriği kaldır → POST /admin/ratings/" + r.getRatingId()
                + "/remove-content (yorum ve fotoğraf silinir, puan kalır)");
        p.add("Bildirimi yok say → POST /admin/ratings/" + r.getRatingId()
                + "/dismiss-reports (yorum yeniden görünür)");
        MailTemplates.Mail mail = MailTemplates.build(subject, heading, p, null, null);
        try {
            mailService.send(notifyTo, mail.subject(), mail.text(), mail.html());
        } catch (RuntimeException e) {
            log.warn("Moderasyon maili gönderilemedi. Değerlendirme {}: {}",
                    r.getRatingId(), e.getMessage());
        }
    }
}
