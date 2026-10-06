package br.com.salonhub.notification;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * E-mails de conta: convite de primeiro acesso e recuperação de senha. O envio (depois do commit,
 * em segundo plano) fica no {@link MailDispatcher}; o visual, no {@link EmailLayout}.
 */
@Component
public class SmtpEmailNotifier implements EmailNotifier {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final MailDispatcher dispatcher;
    private final EmailLayout layout;
    private final Clock clock;

    public SmtpEmailNotifier(MailDispatcher dispatcher, EmailLayout layout, Clock clock) {
        this.dispatcher = dispatcher;
        this.layout = layout;
        this.clock = clock;
    }

    @Override
    public void sendFirstAccessInvitation(String recipientEmail, String recipientName, String establishmentName,
                                          String link, Instant expiresAt) {
        String expiration = DATE_TIME.format(expiresAt.atZone(clock.getZone()));
        dispatcher.send(recipientEmail, "Seu acesso ao " + layout.brandName(), new EmailContent(
                "Ative seu acesso",
                "Olá, " + recipientName + "!",
                List.of("Você foi cadastrado(a) como profissional em " + establishmentName + " no "
                        + layout.brandName() + ". Para ativar seu acesso, defina sua senha pelo link abaixo."),
                List.of(),
                new EmailContent.Action("Definir minha senha", link),
                "O link é de uso único e expira em " + expiration
                        + ". Se você não esperava este convite, ignore este e-mail."));
    }

    @Override
    public void sendPasswordReset(String recipientEmail, String recipientName, String link, long validityMinutes) {
        dispatcher.send(recipientEmail, "Recuperação de senha — " + layout.brandName(), new EmailContent(
                "Redefinir sua senha",
                "Olá, " + recipientName + "!",
                List.of("Recebemos um pedido para redefinir a senha da sua conta no " + layout.brandName()
                        + ". Para criar uma nova senha, use o link abaixo."),
                List.of(),
                new EmailContent.Action("Criar nova senha", link),
                "O link é válido por " + validityMinutes + " minutos e só pode ser usado uma vez. "
                        + "Se você não fez esse pedido, ignore este e-mail: sua senha atual continua valendo."));
    }
}
