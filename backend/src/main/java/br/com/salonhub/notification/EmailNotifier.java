package br.com.salonhub.notification;

import java.time.Instant;

/**
 * Envio de e-mails transacionais. A implementação padrão usa SMTP; nos testes
 * ela é substituída por um mock para verificar o conteúdo sem servidor de e-mail.
 */
public interface EmailNotifier {

    void sendFirstAccessInvitation(String recipientEmail, String recipientName, String establishmentName,
                                   String link, Instant expiresAt);

    void sendPasswordReset(String recipientEmail, String recipientName, String link, long validityMinutes);
}
