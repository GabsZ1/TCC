package br.com.salonhub.notification;

import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.Customer;
import br.com.salonhub.entity.EstablishmentProfile;
import br.com.salonhub.entity.User;
import br.com.salonhub.repository.EstablishmentProfileRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * E-mails sobre agendamentos para o cliente e para o profissional.
 * <ul>
 *   <li>O cliente recebe confirmação, remarcação, cancelamento pelo estabelecimento e lembrete.
 *       Cliente de conta online usa o e-mail da conta; cliente de balcão, o e-mail do cadastro
 *       (sem e-mail, não recebe nada).</li>
 *   <li>O profissional é avisado de agendamentos novos, remarcados ou cancelados na agenda dele,
 *       exceto quando foi ele mesmo quem fez a alteração.</li>
 *   <li>Contas desativadas ou excluídas não recebem e-mail.</li>
 * </ul>
 * Os textos são montados na hora, dentro da transação (os dados do agendamento ainda estão
 * carregados), e o envio acontece depois do commit pelo {@link MailDispatcher}.
 */
@Component
public class AppointmentNotifier {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy 'às' HH:mm", PT_BR);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm", PT_BR);

    /** Situação do agendamento antes de uma remarcação, para mostrar "antes" e "agora". */
    public record PreviousSchedule(User professional, LocalDateTime startsAt, String serviceName) {
        public static PreviousSchedule of(Appointment appointment) {
            return new PreviousSchedule(appointment.getProfessional(), appointment.getStartsAt(),
                    appointment.getServiceName());
        }
    }

    private final MailDispatcher dispatcher;
    private final EstablishmentProfileRepository profiles;
    private final FrontendLinks links;

    public AppointmentNotifier(MailDispatcher dispatcher, EstablishmentProfileRepository profiles,
                               FrontendLinks links) {
        this.dispatcher = dispatcher;
        this.profiles = profiles;
        this.links = links;
    }

    /** Agendamento novo, online ou de balcão. {@code actor} é quem criou. */
    public void booked(Appointment appointment, User actor) {
        sendToClient(appointment, "Agendamento confirmado: " + appointment.getServiceName()
                        + " em " + SHORT_DATE.format(appointment.getStartsAt()),
                "Seu horário está confirmado",
                "Seu agendamento em " + appointment.getEstablishment().getName() + " está confirmado.",
                clientDetails(appointment, null), null);
        if (shouldNotify(appointment.getProfessional(), actor)) {
            sendNewAppointmentToProfessional(appointment);
        }
    }

    /** Troca de horário, de profissional ou de serviço. Não envia nada se nada mudou. */
    public void rescheduled(Appointment appointment, PreviousSchedule before, User actor) {
        boolean sameProfessional = sameUser(before.professional(), appointment.getProfessional());
        if (sameProfessional && before.startsAt().equals(appointment.getStartsAt())
                && Objects.equals(before.serviceName(), appointment.getServiceName())) {
            return;
        }
        boolean changedByClient = sameUser(actor, appointment.getCustomer().getUser());
        sendToClient(appointment, "Agendamento remarcado: " + appointment.getServiceName()
                        + " em " + SHORT_DATE.format(appointment.getStartsAt()),
                "Seu agendamento foi remarcado",
                changedByClient
                        ? "Sua alteração foi registrada. Confira como ficou o agendamento:"
                        : appointment.getEstablishment().getName() + " remarcou o seu agendamento. Confira o novo horário:",
                clientDetails(appointment, before.startsAt()), null);

        if (sameProfessional) {
            if (shouldNotify(appointment.getProfessional(), actor)) {
                sendToProfessional(appointment.getProfessional(),
                        "Agendamento remarcado: " + customerName(appointment) + " em "
                                + SHORT_DATE.format(appointment.getStartsAt()),
                        "Um agendamento mudou de horário",
                        "O agendamento de " + customerName(appointment) + " foi remarcado.",
                        List.of(
                                detail("Cliente", customerName(appointment)),
                                detail("Serviço", appointment.getServiceName()),
                                detail("Novo horário", FULL_DATE.format(appointment.getStartsAt())),
                                detail("Horário anterior", FULL_DATE.format(before.startsAt()))));
            }
            return;
        }
        if (shouldNotify(before.professional(), actor)) {
            sendToProfessional(before.professional(),
                    "Agendamento transferido: " + customerName(appointment) + " em "
                            + SHORT_DATE.format(before.startsAt()),
                    "Um agendamento saiu da sua agenda",
                    "O atendimento abaixo foi transferido para outro profissional, e o horário voltou a ficar livre.",
                    List.of(
                            detail("Cliente", customerName(appointment)),
                            detail("Serviço", before.serviceName()),
                            detail("Data", FULL_DATE.format(before.startsAt()))));
        }
        if (shouldNotify(appointment.getProfessional(), actor)) {
            sendNewAppointmentToProfessional(appointment);
        }
    }

    /** O próprio cliente cancelou: avisa o profissional que o horário ficou livre. */
    public void cancelledByClient(Appointment appointment) {
        if (!shouldNotify(appointment.getProfessional(), null)) return;
        sendToProfessional(appointment.getProfessional(),
                "Agendamento cancelado: " + customerName(appointment) + " em "
                        + SHORT_DATE.format(appointment.getStartsAt()),
                "Um cliente cancelou o horário",
                customerName(appointment) + " cancelou o agendamento abaixo. O horário voltou a ficar livre na sua agenda.",
                professionalDetails(appointment));
    }

    /** O estabelecimento cancelou: avisa o cliente (com o motivo) e o profissional. */
    public void cancelledByEstablishment(Appointment appointment, User actor) {
        List<EmailContent.Detail> details = new ArrayList<>(List.of(
                detail("Estabelecimento", appointment.getEstablishment().getName()),
                detail("Serviço", appointment.getServiceName()),
                detail("Profissional", appointment.getProfessional().getName()),
                detail("Data", FULL_DATE.format(appointment.getStartsAt()))));
        addIfPresent(details, "Motivo", appointment.getCancellationReason());
        sendToClient(appointment, "Agendamento cancelado: " + appointment.getServiceName()
                        + " em " + SHORT_DATE.format(appointment.getStartsAt()),
                "Seu agendamento foi cancelado",
                appointment.getEstablishment().getName() + " cancelou o seu agendamento.",
                details,
                hasAccount(appointment)
                        ? "Se quiser, você pode marcar um novo horário pelo sistema."
                        : contactNote(appointment, "Para marcar um novo horário, fale com o estabelecimento"));

        if (shouldNotify(appointment.getProfessional(), actor)) {
            List<EmailContent.Detail> professionalDetails = new ArrayList<>(professionalDetails(appointment));
            addIfPresent(professionalDetails, "Motivo", appointment.getCancellationReason());
            sendToProfessional(appointment.getProfessional(),
                    "Agendamento cancelado: " + customerName(appointment) + " em "
                            + SHORT_DATE.format(appointment.getStartsAt()),
                    "Um agendamento foi cancelado",
                    "O estabelecimento cancelou o agendamento abaixo. O horário voltou a ficar livre na sua agenda.",
                    professionalDetails);
        }
    }

    /** Lembrete enviado ao cliente perto do horário. Devolve se havia para quem enviar. */
    public boolean reminder(Appointment appointment) {
        return sendToClient(appointment, "Lembrete: " + appointment.getServiceName()
                        + " em " + SHORT_DATE.format(appointment.getStartsAt()),
                "Seu horário está chegando",
                "Passando para lembrar do seu agendamento em " + appointment.getEstablishment().getName() + ".",
                clientDetails(appointment, null), null);
    }

    private boolean sendToClient(Appointment appointment, String subject, String title, String intro,
                                 List<EmailContent.Detail> details, String footnote) {
        String recipient = clientEmail(appointment.getCustomer());
        if (recipient == null) return false;
        boolean account = hasAccount(appointment);
        dispatcher.send(recipient, subject, new EmailContent(
                title,
                "Olá, " + firstName(customerName(appointment)) + "!",
                List.of(intro),
                details,
                account ? new EmailContent.Action("Ver meus agendamentos", links.clientAppointments()) : null,
                footnote != null ? footnote
                        : account
                        ? "Se precisar remarcar ou cancelar, faça isso pela página de agendamentos."
                        : contactNote(appointment, "Para remarcar ou cancelar, fale com o estabelecimento")));
        return true;
    }

    private void sendNewAppointmentToProfessional(Appointment appointment) {
        sendToProfessional(appointment.getProfessional(),
                "Novo agendamento: " + customerName(appointment) + " em "
                        + SHORT_DATE.format(appointment.getStartsAt()),
                "Novo agendamento na sua agenda",
                customerName(appointment) + " tem um horário marcado com você.",
                professionalDetails(appointment));
    }

    private void sendToProfessional(User professional, String subject, String title, String intro,
                                    List<EmailContent.Detail> details) {
        dispatcher.send(professional.getEmail(), subject, new EmailContent(
                title,
                "Olá, " + firstName(professional.getName()) + "!",
                List.of(intro),
                details,
                new EmailContent.Action("Abrir minha agenda", links.professionalAgenda()),
                null));
    }

    private List<EmailContent.Detail> clientDetails(Appointment appointment, LocalDateTime previousStart) {
        List<EmailContent.Detail> details = new ArrayList<>(List.of(
                detail("Estabelecimento", appointment.getEstablishment().getName()),
                detail("Serviço", appointment.getServiceName()),
                detail("Profissional", appointment.getProfessional().getName()),
                detail(previousStart == null ? "Data" : "Novo horário", FULL_DATE.format(appointment.getStartsAt()))));
        if (previousStart != null) {
            details.add(detail("Horário anterior", FULL_DATE.format(previousStart)));
        }
        details.add(detail("Duração", duration(appointment.getDurationMinutes())));
        details.add(detail("Valor", money(appointment.getAgreedPrice())));
        addIfPresent(details, "Endereço", address(appointment));
        return details;
    }

    private List<EmailContent.Detail> professionalDetails(Appointment appointment) {
        return List.of(
                detail("Cliente", customerName(appointment)),
                detail("Serviço", appointment.getServiceName()),
                detail("Data", FULL_DATE.format(appointment.getStartsAt())),
                detail("Duração", duration(appointment.getDurationMinutes())));
    }

    private String address(Appointment appointment) {
        return profiles.findByEstablishmentId(appointment.getEstablishment().getId())
                .map(AppointmentNotifier::formatAddress)
                .orElse(null);
    }

    private static String formatAddress(EstablishmentProfile profile) {
        String street = joinNonBlank(", ", profile.getStreet(), profile.getStreetNumber());
        String city = joinNonBlank("/", profile.getCity(), profile.getState());
        String full = joinNonBlank(" - ", street, profile.getNeighborhood(), city);
        return full.isBlank() ? null : full;
    }

    private String contactNote(Appointment appointment, String prefix) {
        String contact = profiles.findByEstablishmentId(appointment.getEstablishment().getId())
                .map(profile -> isBlank(profile.getWhatsapp()) ? profile.getPhone() : profile.getWhatsapp())
                .filter(value -> !isBlank(value))
                .orElse(null);
        return contact == null ? prefix + "." : prefix + " pelo telefone " + contact + ".";
    }

    private static String clientEmail(Customer customer) {
        if (customer.getUser() != null) {
            User user = customer.getUser();
            return user.isActive() && !isBlank(user.getEmail()) ? user.getEmail() : null;
        }
        return isBlank(customer.getEmail()) ? null : customer.getEmail();
    }

    private static boolean hasAccount(Appointment appointment) {
        return appointment.getCustomer().getUser() != null;
    }

    private static String customerName(Appointment appointment) {
        return appointment.getCustomer().getName();
    }

    private static boolean shouldNotify(User professional, User actor) {
        return professional != null && professional.isActive() && !isBlank(professional.getEmail())
                && !sameUser(professional, actor);
    }

    private static boolean sameUser(User first, User second) {
        return first != null && second != null && Objects.equals(first.getId(), second.getId());
    }

    private static EmailContent.Detail detail(String label, String value) {
        return new EmailContent.Detail(label, value);
    }

    private static void addIfPresent(List<EmailContent.Detail> details, String label, String value) {
        if (!isBlank(value)) details.add(detail(label, value));
    }

    private static String firstName(String name) {
        if (isBlank(name)) return "";
        return name.trim().split("\\s+")[0];
    }

    private static String duration(Integer minutes) {
        if (minutes == null) return "";
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (hours == 0) return rest + " min";
        return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
    }

    private static String money(BigDecimal value) {
        return value == null ? "" : NumberFormat.getCurrencyInstance(PT_BR).format(value);
    }

    private static String joinNonBlank(String separator, String... values) {
        return Stream.of(values).filter(value -> !isBlank(value)).map(String::trim)
                .collect(Collectors.joining(separator));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
