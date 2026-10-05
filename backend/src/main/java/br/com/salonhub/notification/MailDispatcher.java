package br.com.salonhub.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Envia os e-mails por SMTP em segundo plano (RNF.03.01 da US 2.20), só depois que a transação
 * que os gerou for confirmada: se a operação falhar, nenhum e-mail sobre ela é enviado. Falha no
 * servidor de e-mail não derruba a operação; fica registrada no log.
 * <p>
 * Cada e-mail vai em duas versões (texto puro e HTML) na mesma mensagem.
 */
@Component
public class MailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final EmailLayout layout;
    private final String from;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public MailDispatcher(ObjectProvider<JavaMailSender> mailSender, EmailLayout layout,
                          @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.layout = layout;
        this.from = from;
    }

    /** Monta o e-mail agora (com os dados da transação atual) e agenda o envio para depois do commit. */
    public void send(String to, String subject, EmailContent content) {
        EmailContent.Rendered rendered = layout.render(content);
        Runnable sendInBackground = () -> executor.execute(() -> deliver(to, subject, rendered));
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

    private void deliver(String to, String subject, EmailContent.Rendered rendered) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Servidor de e-mail não configurado; e-mail \"{}\" para {} não foi enviado.", subject, to);
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(rendered.plainText(), rendered.html());
            sender.send(message);
            log.info("E-mail \"{}\" enviado para {}.", subject, to);
        } catch (MailException | MessagingException exception) {
            log.warn("Falha ao enviar o e-mail \"{}\" para {}: {}", subject, to, exception.getMessage());
        }
    }
}
