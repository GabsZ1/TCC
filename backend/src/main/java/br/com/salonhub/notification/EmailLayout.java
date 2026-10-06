package br.com.salonhub.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Modelo visual único dos e-mails: gera a versão em HTML (com estilos inline, que é o que os
 * clientes de e-mail aceitam) e a versão em texto puro, para quem não exibe HTML.
 * O nome do produto vem de {@code app.mail.brand-name}.
 */
@Component
public class EmailLayout {

    private static final String PRIMARY = "#5b4bdb";
    private static final String TEXT = "#1f2333";
    private static final String MUTED = "#6b7080";
    private static final String BORDER = "#e6e8f0";
    private static final String BACKGROUND = "#f5f7fb";

    private final String brandName;

    public EmailLayout(@Value("${app.mail.brand-name:Meu Espaço}") String brandName) {
        this.brandName = brandName;
    }

    public String brandName() {
        return brandName;
    }

    public EmailContent.Rendered render(EmailContent content) {
        return new EmailContent.Rendered(plainText(content), html(content));
    }

    private String plainText(EmailContent content) {
        StringBuilder text = new StringBuilder();
        text.append(content.greeting()).append("\n\n");
        content.paragraphs().forEach(paragraph -> text.append(paragraph).append("\n\n"));
        if (!content.details().isEmpty()) {
            content.details().forEach(detail ->
                    text.append(detail.label()).append(": ").append(detail.value()).append('\n'));
            text.append('\n');
        }
        if (content.action() != null) {
            text.append(content.action().label()).append(": ").append(content.action().url()).append("\n\n");
        }
        if (content.footnote() != null) {
            text.append(content.footnote()).append("\n\n");
        }
        text.append("Equipe ").append(brandName).append('\n');
        return text.toString();
    }

    private String html(EmailContent content) {
        StringBuilder body = new StringBuilder();
        body.append(paragraph(escape(content.greeting()), TEXT, "16px", true));
        content.paragraphs().forEach(paragraph -> body.append(paragraph(escape(paragraph), TEXT, "15px", false)));

        if (!content.details().isEmpty()) {
            body.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                    .append("style=\"margin:8px 0 20px;border:1px solid ").append(BORDER)
                    .append(";border-radius:12px;border-collapse:separate;\">");
            content.details().forEach(detail -> body
                    .append("<tr><td style=\"padding:10px 16px;color:").append(MUTED)
                    .append(";font-size:13px;width:38%;vertical-align:top;\">").append(escape(detail.label()))
                    .append("</td><td style=\"padding:10px 16px;color:").append(TEXT)
                    .append(";font-size:14px;font-weight:600;\">").append(escape(detail.value()))
                    .append("</td></tr>"));
            body.append("</table>");
        }

        if (content.action() != null) {
            body.append("<p style=\"margin:8px 0 24px;\"><a href=\"").append(escape(content.action().url()))
                    .append("\" style=\"display:inline-block;background:").append(PRIMARY)
                    .append(";color:#ffffff;text-decoration:none;font-weight:700;font-size:14px;")
                    .append("padding:12px 22px;border-radius:10px;\">")
                    .append(escape(content.action().label())).append("</a></p>");
        }
        if (content.footnote() != null) {
            body.append(paragraph(escape(content.footnote()), MUTED, "13px", false));
        }

        return """
                <!doctype html>
                <html lang="pt-BR">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                <body style="margin:0;padding:0;background:%s;font-family:Arial,Helvetica,sans-serif;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:%s;padding:24px 12px;">
                <tr><td align="center">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border:1px solid %s;border-radius:16px;">
                <tr><td style="padding:22px 28px;border-bottom:1px solid %s;">
                <span style="color:%s;font-size:18px;font-weight:800;">%s</span>
                </td></tr>
                <tr><td style="padding:28px;">
                <h1 style="margin:0 0 18px;color:%s;font-size:22px;line-height:1.3;">%s</h1>
                %s
                </td></tr>
                <tr><td style="padding:16px 28px;border-top:1px solid %s;color:%s;font-size:12px;">
                Equipe %s · Este é um e-mail automático; não é preciso respondê-lo.
                </td></tr>
                </table>
                </td></tr>
                </table>
                </body>
                </html>
                """.formatted(BACKGROUND, BACKGROUND, BORDER, BORDER, PRIMARY, escape(brandName),
                TEXT, escape(content.title()), body, BORDER, MUTED, escape(brandName));
    }

    private static String paragraph(String html, String color, String size, boolean strong) {
        return "<p style=\"margin:0 0 14px;color:" + color + ";font-size:" + size + ";line-height:1.55;"
                + (strong ? "font-weight:700;" : "") + "\">" + html + "</p>";
    }

    private static String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
