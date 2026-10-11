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
        return build(subject, heading, paragraphs, null, List.of(), buttonLabel, buttonUrl);
    }

    /**
     * Ortasında büyük, harf aralıklı bir kod kutusu olan mail (şifre sıfırlama,
     * doğrulama). [before] koddan önce, [after] sonra gelen paragraflar.
     */
    public static Mail withCode(String subject, String heading, List<String> before,
                                String code, List<String> after) {
        return build(subject, heading, before, code, after, null, null);
    }

    private static Mail build(String subject, String heading, List<String> paragraphs,
                              String code, List<String> after,
                              String buttonLabel, String buttonUrl) {
        StringBuilder text = new StringBuilder(heading).append("\n\n");
        StringBuilder body = new StringBuilder();
        for (String p : paragraphs) {
            text.append(p).append("\n\n");
            body.append("<p style=\"margin:0 0 16px;font-size:15px;line-height:1.5;color:#1b1a18\">")
                    .append(HtmlUtils.htmlEscape(p)).append("</p>");
        }
        if (code != null) {
            text.append(code).append("\n\n");
            body.append("<div style=\"margin:8px 0 24px;padding:16px 0;text-align:center;background:#ffffff;"
                    + "border-radius:12px;font-size:32px;font-weight:700;letter-spacing:10px;color:#1b1a18;"
                    + "font-family:SFMono-Regular,Menlo,Consolas,monospace\">")
                    .append(HtmlUtils.htmlEscape(code)).append("</div>");
            for (String p : after) {
                text.append(p).append("\n\n");
                body.append("<p style=\"margin:0 0 16px;font-size:15px;line-height:1.5;color:#1b1a18\">")
                        .append(HtmlUtils.htmlEscape(p)).append("</p>");
            }
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
        // Önizleme metni (preheader): posta uygulaması listede ve bildirimde
        // konunun yanında mailin ilk yazısını gösteriyor; bu gizli metin
        // olmadan oraya logo yazısı ("decouva") düşüyordu. Ardındaki boş
        // karakterler, önizlemeye gövdeden başka yazı karışmasını engeller.
        String preheader = paragraphs.isEmpty() ? heading
                : paragraphs.get(paragraphs.size() > 1 ? 1 : 0);
        String html = "<!doctype html><html lang=\"tr\"><body style=\"margin:0;padding:0;background:#f5f4f2\">"
                + "<div style=\"display:none;font-size:1px;line-height:1px;max-height:0;max-width:0;opacity:0;"
                + "overflow:hidden;mso-hide:all\">" + HtmlUtils.htmlEscape(preheader)
                + "&#847;&zwnj;&nbsp;".repeat(60) + "</div>"
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
