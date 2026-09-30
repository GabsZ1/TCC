package br.com.salonhub.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Envia os e-mails por SMTP em segundo plano (RNF.03.01 da US 2.20), só depois
 * que a transação que gerou o token for confirmada: se o cadastro ou o pedido
 * falhar, nenhum e-mail com link inválido é enviado. Falha no servidor de e-mail
 * não derruba a operação — fica registrada no log.
 */
@Component
public class SmtpEmailNotifier implements EmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailNotifier.class);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SmtpEmailNotifier(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.mail.from}") String from,
            Clock clock) {
        this.mailSender = mailSender;
        this.from = from;
        this.clock = clock;
    }

    @Override
    public void sendFirstAccessInvitation(String recipientEmail, String recipientName, String establishmentName,
                                          String link, Instant expiresAt) {
        String expiration = DATE_TIME.format(expiresAt.atZone(clock.getZone()));
        String body = """
                Olá, %s!

                Você foi cadastrado(a) como profissional em %s no Meu Espaço.
                Para ativar seu acesso, defina sua senha pelo link abaixo:

                %s

                O link é de uso único e expira em %s.
                Se você não esperava este convite, ignore este e-mail.

                Equipe Meu Espaço
                """.formatted(recipientName, establishmentName, link, expiration);
        dispatch(recipientEmail, "Seu acesso ao Meu Espaço", body);
    }

    @Override
    public void sendPasswordReset(String recipientEmail, String recipientName, String link, long validityMinutes) {
        String body = """
                Olá, %s!

                Recebemos um pedido para redefinir a senha da sua conta no Meu Espaço.
                Para criar uma nova senha, acesse o link abaixo:

                %s

                O link é válido por %d minutos e só pode ser usado uma vez.
                Se você não fez esse pedido, ignore este e-mail: sua senha atual continua valendo.

                Equipe Meu Espaço
                """.formatted(recipientName, link, validityMinutes);
        dispatch(recipientEmail, "Recuperação de senha — Meu Espaço", body);
    }

    private void dispatch(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        Runnable sendInBackground = () -> executor.execute(() -> send(message));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendInBackground.run();
                }
            });
        } else {
            sendInBackground.run();
        }
    }

    private void send(SimpleMailMessage message) {
        JavaMailSender sender = mailSender.getIfAvailable();
        String recipient = String.join(", ", message.getTo());
        if (sender == null) {
            log.warn("Servidor de e-mail não configurado; e-mail \"{}\" para {} não foi enviado.",
                    message.getSubject(), recipient);
            return;
        }
        try {
            sender.send(message);
            log.info("E-mail \"{}\" enviado para {}.", message.getSubject(), recipient);
        } catch (MailException exception) {
            log.warn("Falha ao enviar o e-mail \"{}\" para {}: {}",
                    message.getSubject(), recipient, exception.getMessage());
        }
    }
}
