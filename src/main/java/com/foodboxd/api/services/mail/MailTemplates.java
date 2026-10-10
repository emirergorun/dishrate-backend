package com.foodboxd.api.services.mail;

import org.springframework.web.util.HtmlUtils;

import java.util.List;

/**
 * Mail şablonu: sade, Türkçe, HTML + düz metin (2.6). Şifre sıfırlama (2.7)
 * ve doğrulama (2.8) bunu kullanır. Görsel yok (çoğu posta uygulaması
 * görselleri engelliyor); marka adı yazı, renk marka turuncusu.
 */
public final class MailTemplates {

    private MailTemplates() {}

    private static final String BRAND = "decouva";
    private static final String ORANGE = "#FF6B35";

    public record Mail(String subject, String text, String html) {}

    /**
     * @param paragraphs düz metin paragraflar (HTML'e kaçışlanarak girer)
     * @param buttonLabel düğme yazısı; null ise düğme yok
     * @param buttonUrl düğmenin adresi
     */
    public static Mail build(String subject, String heading, List<String> paragraphs,
                             String buttonLabel, String buttonUrl) {
        StringBuilder text = new StringBuilder(heading).append("\n\n");
        StringBuilder body = new StringBuilder();
        for (String p : paragraphs) {
            text.append(p).append("\n\n");
            body.append("<p style=\"margin:0 0 16px;font-size:15px;line-height:1.5;color:#1b1a18\">")
                    .append(HtmlUtils.htmlEscape(p)).append("</p>");
        }
        if (buttonLabel != null && buttonUrl != null) {
            text.append(buttonLabel).append(": ").append(buttonUrl).append("\n\n");
            body.append("<p style=\"margin:24px 0\"><a href=\"").append(HtmlUtils.htmlEscape(buttonUrl))
                    .append("\" style=\"background:").append(ORANGE)
                    .append(";color:#ffffff;text-decoration:none;padding:12px 22px;border-radius:10px;"
                            + "font-weight:600;font-size:15px;display:inline-block\">")
                    .append(HtmlUtils.htmlEscape(buttonLabel)).append("</a></p>");
        }
        text.append("— Decouva");
        String html = "<!doctype html><html lang=\"tr\"><body style=\"margin:0;padding:0;background:#f5f4f2\">"
                + "<div style=\"max-width:520px;margin:0 auto;padding:32px 24px;font-family:-apple-system,"
                + "Segoe UI,Roboto,Helvetica,Arial,sans-serif\">"
                + "<div style=\"font-size:26px;font-weight:700;color:" + ORANGE + ";margin-bottom:24px\">"
                + BRAND + "</div>"
                + "<h1 style=\"font-size:20px;margin:0 0 16px;color:#1b1a18\">"
                + HtmlUtils.htmlEscape(heading) + "</h1>"
                + body
                + "<p style=\"margin:32px 0 0;font-size:12px;color:#8a857e\">Bu mail Decouva hesabınla ilgili "
                + "olarak gönderildi. Sorun olursa bu maili yanıtlaman yeterli.</p>"
                + "</div></body></html>";
        return new Mail(subject, text.toString(), html);
    }
}
