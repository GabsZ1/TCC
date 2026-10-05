package br.com.salonhub.notification;

import java.util.List;

/**
 * Conteúdo de um e-mail antes de virar texto e HTML no {@link EmailLayout}.
 *
 * @param title      título em destaque no topo do e-mail
 * @param greeting   saudação ("Olá, Maria!")
 * @param paragraphs parágrafos de texto
 * @param details    quadro de informações (rótulo e valor), opcional
 * @param action     botão com link para o sistema, opcional
 * @param footnote   observação em letra menor no fim, opcional
 */
public record EmailContent(
        String title,
        String greeting,
        List<String> paragraphs,
        List<Detail> details,
        Action action,
        String footnote) {

    public EmailContent {
        paragraphs = paragraphs == null ? List.of() : List.copyOf(paragraphs);
        details = details == null ? List.of() : List.copyOf(details);
    }

    public record Detail(String label, String value) {
    }

    public record Action(String label, String url) {
    }

    /** O e-mail pronto: versão em texto puro e versão em HTML. */
    public record Rendered(String plainText, String html) {
    }
}
