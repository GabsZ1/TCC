package br.com.salonhub.seed;

import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentSource;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.Customer;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentBusinessHour;
import br.com.salonhub.entity.EstablishmentProfile;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.ProfessionalBusinessHour;
import br.com.salonhub.entity.ProfessionalCalendarEvent;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.CustomerRepository;
import br.com.salonhub.repository.EstablishmentBusinessHourRepository;
import br.com.salonhub.repository.EstablishmentProfileRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import br.com.salonhub.repository.ProfessionalBusinessHourRepository;
import br.com.salonhub.repository.ProfessionalCalendarEventRepository;
import br.com.salonhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Dados de demonstração para ver o sistema funcionando: estabelecimentos (7 aceitando
 * agendamentos, para a paginação de 6 aparecer, e 1 pausado), perfis públicos, horários,
 * serviços, funcionários, clientes e agendamentos dos últimos 6 meses e dos próximos 14 dias
 * (concluídos, cancelados, invalidados e confirmados). Seis meses de histórico deixam os
 * relatórios financeiro e de comissões com dados em qualquer mês recente e permitem comparar meses.
 *
 * Idempotente: se o administrador do primeiro estabelecimento já existir, não faz nada.
 * Os dados são gerados com semente fixa, então rodar em bancos limpos gera sempre o mesmo resultado
 * (as datas acompanham o dia em que o comando roda).
 */
@Service
@Profile("seed")
@RequiredArgsConstructor
public class DemoDataSeeder {

    static final String PASSWORD = "Demo@123";
    static final String SYSTEM_ADMIN_EMAIL = "plataforma@salonhub.demo";

    private static final LocalTime[] SLOTS = {
            LocalTime.of(9, 0), LocalTime.of(10, 30), LocalTime.of(13, 30), LocalTime.of(15, 0), LocalTime.of(16, 30)};
    private static final int PAST_DAYS = 180;
    private static final int FUTURE_DAYS = 14;

    private final EstablishmentRepository establishmentRepository;
    private final EstablishmentProfileRepository profileRepository;
    private final EstablishmentBusinessHourRepository businessHourRepository;
    private final EstablishmentServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final ProfessionalBusinessHourRepository professionalHourRepository;
    private final ProfessionalCalendarEventRepository calendarEventRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    private final Random random = new Random(20260928L);
    private String encodedPassword;
    private int documentSequence;

    public record Result(int establishments, int professionals, int clients, int appointments) {
    }

    /** Retorna null quando os dados já existem. */
    @Transactional
    public Result seed() {
        if (userRepository.existsByEmailIgnoreCase("admin@studiobella.demo")) {
            return null;
        }
        encodedPassword = passwordEncoder.encode(PASSWORD);
        LocalDate today = LocalDate.now(clock);

        if (!userRepository.existsByEmailIgnoreCase(SYSTEM_ADMIN_EMAIL)) {
            userRepository.save(User.builder().name("Administração Meu Espaço").email(SYSTEM_ADMIN_EMAIL)
                    .password(encodedPassword).role(UserRole.SYSTEM_ADMIN).build());
        }

        List<User> clients = createClients();
        List<SeededEstablishment> establishments = new ArrayList<>();

        // ---------- Studio Bella: o mais completo ----------
        SeededEstablishment bella = establishment("Studio Bella", "studiobella", "Ana Beatriz Souza",
                "Salão completo no centro de Maringá: cortes, coloração, tratamentos e unhas. Atendimento com hora marcada e café por nossa conta.",
                "@studiobella.mga", "44999110001", "87013000", "Avenida Brasil", "3250", "Sala 2", "Centro", "Maringá", "PR");
        EstablishmentService corteFeminino = service(bella, "Corte feminino", "Lavagem, corte e finalização.", "90.00", "40", 60);
        EstablishmentService escova = service(bella, "Escova modelada", null, "60.00", "40", 45);
        EstablishmentService coloracao = service(bella, "Coloração", "Coloração completa com produtos profissionais.", "180.00", "35", 90);
        EstablishmentService mechas = service(bella, "Mechas", "Luzes ou mechas, sem tonalização.", "250.00", "35", 90);
        EstablishmentService hidratacao = service(bella, "Hidratação profunda", null, "80.00", "40", 45);
        EstablishmentService manicure = service(bella, "Manicure", null, "35.00", "50", 45);
        EstablishmentService pedicure = service(bella, "Pedicure", null, "40.00", "50", 45);
        EstablishmentService designSobrancelha = service(bella, "Design de sobrancelha", null, "45.00", "45", 30);
        service(bella, "Progressiva", "Serviço desativado para testar a listagem de desativados.", "300.00", "30", 90).deactivate();
        professional(bella, "Camila Rocha", "camila", "Cabeleireiro(a)", "44998760001", "@camila.hair", 2021, List.of());
        professional(bella, "Juliana Mendes", "juliana", "Colorista", "44998760002", "@ju.cores",
                2022, List.of(coloracao, mechas, hidratacao, escova));
        professional(bella, "Patrícia Lima", "patricia", "Manicure", "44998760003", null,
                2023, List.of(manicure, pedicure, designSobrancelha));
        professional(bella, "Rafael Torres", "rafael", "Cabeleireiro(a)", "44998760004", "@rafa.tesoura",
                2024, List.of(corteFeminino, escova, hidratacao));
        User inativo = professional(bella, "Bruno Alves", "bruno", "Cabeleireiro(a)", "44998760005", null, 2020, List.of());
        inativo.deactivate();
        bella.professionals().remove(inativo);
        // Camila trabalha só à tarde às quintas e tem um curso daqui a 7 dias (aparece na agenda dela).
        professionalHourRepository.save(ProfessionalBusinessHour.create(
                bella.professionals().getFirst(), DayOfWeek.THURSDAY, false, LocalTime.of(13, 0), LocalTime.of(18, 0)));
        LocalDate courseDay = nextWorkingDay(today.plusDays(7));
        calendarEventRepository.save(ProfessionalCalendarEvent.create(bella.establishment(), bella.professionals().getFirst(),
                "Curso de coloração", "Dia de curso, sem atendimentos.", courseDay.atStartOfDay(), courseDay.plusDays(1).atStartOfDay(), true));
        bella.blockedDays().add(new BlockedDay(bella.professionals().getFirst(), courseDay));
        establishments.add(bella);

        // ---------- Barbearia Dom Corte ----------
        SeededEstablishment domCorte = establishment("Barbearia Dom Corte", "domcorte", "Diego Martins",
                "Barbearia clássica com toalha quente, navalha e cerveja gelada. Agende pelo Meu Espaço.",
                "@domcorte", "44999220002", "87020025", "Avenida Herval", "780", null, "Zona 7", "Maringá", "PR");
        service(domCorte, "Corte masculino", null, "50.00", "45", 45);
        service(domCorte, "Barba", "Barba com toalha quente e navalha.", "40.00", "45", 30);
        service(domCorte, "Corte + barba", null, "80.00", "45", 60);
        service(domCorte, "Pigmentação", null, "60.00", "40", 45);
        service(domCorte, "Sobrancelha na navalha", null, "20.00", "50", 30);
        professional(domCorte, "Lucas Ferreira", "lucas", "Barbeiro(a)", "44997650001", "@lucas.barber", 2021, List.of());
        professional(domCorte, "Thiago Nunes", "thiago", "Barbeiro(a)", "44997650002", "@thiago.fade", 2023, List.of());
        professional(domCorte, "Gustavo Ribeiro", "gustavo", "Barbeiro(a)", "44997650003", null, 2025, List.of());
        establishments.add(domCorte);

        // ---------- Estabelecimentos menores (para a paginação do cliente) ----------
        establishments.add(small("Espaço Zen Estética", "espacozen", "Fernanda Costa", "Estética facial e corporal em ambiente tranquilo.",
                "Londrina", "PR", "86010000", "Esteticista", "Limpeza de pele", "120.00", 60, "Drenagem linfática", "110.00", 60, "Massagem relaxante", "130.00", 60));
        establishments.add(small("Nail Design Lu", "naildesignlu", "Luana Pereira", "Unhas em gel, fibra e nail art.",
                "Maringá", "PR", "87015000", "Manicure", "Alongamento em gel", "150.00", 90, "Manutenção de gel", "90.00", 60, "Esmaltação em gel", "60.00", 45));
        establishments.add(small("Cílios & Cia", "ciliosecia", "Renata Gomes", "Extensão de cílios fio a fio e volume russo.",
                "Sarandi", "PR", "87111000", "Lash designer", "Extensão fio a fio", "160.00", 90, "Volume russo", "200.00", 90, "Manutenção de cílios", "90.00", 60));
        establishments.add(small("Salão Raízes", "salaoraizes", "Joana Batista", "Especialistas em cabelos crespos e cacheados.",
                "Maringá", "PR", "87030000", "Cabeleireiro(a)", "Corte para cacheadas", "100.00", 60, "Tranças", "220.00", 90, "Umectação", "70.00", 45));
        establishments.add(small("Barbearia Navalha de Ouro", "navalhadeouro", "Marcos Vieira", "Cortes modernos e degradê.",
                "Paiçandu", "PR", "87140000", "Barbeiro(a)", "Degradê", "45.00", 45, "Barba completa", "35.00", 30, "Platinado", "150.00", 90));
        SeededEstablishment aurora = small("Studio Aurora", "studioaurora", "Aurora Dias", "Pausado para mostrar que não aparece para clientes.",
                "Maringá", "PR", "87050000", "Cabeleireiro(a)", "Corte", "70.00", 45, "Escova", "50.00", 45, "Penteado", "120.00", 60);
        aurora.establishment().pause(false);

        int appointments = 0;
        for (SeededEstablishment seeded : establishments) {
            double busyness = seeded == bella || seeded == domCorte ? 0.5 : 0.18;
            appointments += createAppointments(seeded, clients, today, busyness);
        }
        int professionals = establishments.stream().mapToInt(s -> s.professionals().size()).sum()
                + aurora.professionals().size() + 1;
        return new Result(establishments.size() + 1, professionals, clients.size(), appointments);
    }

    /**
     * Indica se os dados de demonstração existem, mas ficaram velhos: as datas são geradas a partir
     * do dia em que o seed rodou, então, com o passar dos meses, o mês atual fica sem atendimentos
     * concluídos e os relatórios (que abrem no mês corrente) aparecem vazios.
     */
    @Transactional(readOnly = true)
    public boolean isOutdated() {
        return userRepository.findByEmailIgnoreCase("admin@studiobella.demo")
                .map(admin -> !appointmentRepository.existsByEstablishmentIdAndStatusAndCompletedAtGreaterThanEqual(
                        admin.getEstablishment().getId(), AppointmentStatus.COMPLETED,
                        YearMonth.now(clock).atDay(1).atStartOfDay()))
                .orElse(false);
    }

    // ---------- estabelecimentos ----------

    private SeededEstablishment establishment(String name, String slug, String ownerName, String about, String instagram,
                                              String whatsapp, String postalCode, String street, String number,
                                              String complement, String neighborhood, String city, String state) {
        Establishment establishment = establishmentRepository.save(Establishment.builder()
                .name(name).cnpj(nextCnpj()).email("contato@" + slug + ".demo").build());
        profileRepository.save(EstablishmentProfile.builder().establishment(establishment)
                .aboutUs(about).instagram(instagram).whatsapp(whatsapp).phone(whatsapp)
                .postalCode(postalCode).street(street).streetNumber(number).addressComplement(complement)
                .neighborhood(neighborhood).city(city).state(state)
                .latitude(new BigDecimal(city.equals("Londrina") ? "-23.310280" : city.equals("Sarandi") ? "-23.443690" : city.equals("Paiçandu") ? "-23.457240" : "-23.420999"))
                .longitude(new BigDecimal(city.equals("Londrina") ? "-51.162780" : city.equals("Sarandi") ? "-51.873720" : city.equals("Paiçandu") ? "-52.048610" : "-51.933056")).build());
        for (DayOfWeek day : DayOfWeek.values()) {
            boolean closed = day == DayOfWeek.SUNDAY;
            LocalTime closing = day == DayOfWeek.SATURDAY ? LocalTime.of(14, 0) : LocalTime.of(18, 0);
            businessHourRepository.save(EstablishmentBusinessHour.builder().establishment(establishment).dayOfWeek(day)
                    .closed(closed).openingTime(closed ? null : LocalTime.of(9, 0)).closingTime(closed ? null : closing).build());
        }
        userRepository.save(User.builder().establishment(establishment).name(ownerName)
                .email("admin@" + slug + ".demo").password(encodedPassword).role(UserRole.ADMIN).build());
        return new SeededEstablishment(establishment, slug, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    private SeededEstablishment small(String name, String slug, String ownerName, String about, String city, String state,
                                      String postalCode, String occupation,
                                      String s1, String p1, int d1, String s2, String p2, int d2, String s3, String p3, int d3) {
        SeededEstablishment seeded = establishment(name, slug, ownerName, about, "@" + slug, null, postalCode,
                "Rua das Flores", String.valueOf(100 + random.nextInt(900)), null, "Centro", city, state);
        service(seeded, s1, null, p1, "40", d1);
        service(seeded, s2, null, p2, "40", d2);
        service(seeded, s3, null, p3, "40", d3);
        String firstName = ownerName.split(" ")[0];
        professional(seeded, firstName + " " + ownerName.split(" ")[1], firstName.toLowerCase(), occupation,
                "44996" + String.format("%06d", 100000 + random.nextInt(899999)), null, 2022, List.of());
        return seeded;
    }

    private EstablishmentService service(SeededEstablishment seeded, String name, String description,
                                         String price, String commission, int duration) {
        EstablishmentService service = serviceRepository.save(EstablishmentService.builder()
                .establishment(seeded.establishment()).name(name).description(description)
                .price(new BigDecimal(price)).commissionPercentage(new BigDecimal(commission))
                .durationMinutes(duration).build());
        seeded.services().add(service);
        return service;
    }

    /** services vazio = atende todos os serviços do estabelecimento. */
    private User professional(SeededEstablishment seeded, String name, String emailName, String occupation, String phone,
                              String instagram, int hireYear, List<EstablishmentService> services) {
        User professional = userRepository.save(User.builder().establishment(seeded.establishment())
                .name(name).email(emailName + "@" + seeded.slug() + ".demo").password(encodedPassword)
                .role(UserRole.PROFESSIONAL).phone(phone).occupation(occupation).instagram(instagram)
                .cpf(nextCpf()).hireDate(LocalDate.of(hireYear, 1 + random.nextInt(12), 1 + random.nextInt(28)))
                .address(seeded.establishment().getName().startsWith("Barbearia") ? null : "Rua Santos Dumont, " + (200 + random.nextInt(800)) + ", Maringá - PR")
                .build());
        professional.updateOfferedServices(services.isEmpty(), services);
        seeded.professionals().add(professional);
        return professional;
    }

    private List<User> createClients() {
        String[][] data = {
                {"Maria Oliveira", "maria", "44991230001"}, {"João Silva", "joao", "44991230002"},
                {"Larissa Santos", "larissa", "44991230003"}, {"Pedro Almeida", "pedro", "44991230004"},
                {"Beatriz Carvalho", "beatriz", "44991230005"}, {"Gabriel Souza", "gabriel", "44991230006"},
                {"Amanda Ribeiro", "amanda", "44991230007"}, {"Felipe Moreira", "felipe", "44991230008"},
                {"Carolina Duarte", "carolina", "44991230009"}, {"Rodrigo Castro", "rodrigo", "44991230010"}};
        List<User> clients = new ArrayList<>();
        for (String[] client : data) {
            clients.add(userRepository.save(User.builder().name(client[0]).email(client[1] + "@cliente.demo")
                    .password(encodedPassword).role(UserRole.CLIENT).phone(client[2]).build()));
        }
        return clients;
    }

    // ---------- agendamentos ----------

    /** Distribui reservas passadas e futuras conforme a ocupação prevista para a unidade de demonstração. */
    private int createAppointments(SeededEstablishment seeded, List<User> clients, LocalDate today, double busyness) {
        User administrator = userRepository.findByEmailIgnoreCase("admin@" + seeded.slug() + ".demo").orElseThrow();
        List<Appointment> created = new ArrayList<>();
        for (int offset = -PAST_DAYS; offset <= FUTURE_DAYS; offset++) {
            LocalDate day = today.plusDays(offset);
            if (offset == 0 || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            LocalTime closing = day.getDayOfWeek() == DayOfWeek.SATURDAY ? LocalTime.of(14, 0) : LocalTime.of(18, 0);
            for (User professional : seeded.professionals()) {
                if (seeded.blockedDays().contains(new BlockedDay(professional, day))) {
                    continue;
                }
                List<EstablishmentService> offered = seeded.services().stream()
                        .filter(EstablishmentService::isActive).filter(professional::offers).toList();
                for (LocalTime slot : SLOTS) {
                    if (random.nextDouble() >= (offset > 0 ? busyness * 0.6 : busyness)) {
                        continue;
                    }
                    if (professional == seeded.professionals().getFirst() && day.getDayOfWeek() == DayOfWeek.THURSDAY
                            && slot.isBefore(LocalTime.of(13, 0)) && seeded.slug().equals("studiobella")) {
                        continue;
                    }
                    EstablishmentService service = offered.get(random.nextInt(offered.size()));
                    if (slot.plusMinutes(service.getDurationMinutes()).isAfter(closing)) {
                        continue;
                    }
                    // Maria recebe mais agendamentos para a tela "Meus agendamentos" ficar cheia.
                    User client = random.nextDouble() < 0.25 ? clients.getFirst() : clients.get(random.nextInt(clients.size()));
                    Customer customer = customerRepository.findByEstablishmentIdAndUserId(
                            seeded.establishment().getId(), client.getId()).orElseGet(() -> {
                        Customer newCustomer = Customer.create(seeded.establishment(), client.getName(),
                                client.getPhone(), client.getEmail());
                        newCustomer.link(client);
                        return customerRepository.save(newCustomer);
                    });
                    Appointment appointment = Appointment.schedule(seeded.establishment(), customer, professional, service, day.atTime(slot));
                    appointment.recordCreation(br.com.salonhub.entity.AppointmentSource.CLIENT_ONLINE, client);
                    applyStatus(appointment, offset, administrator);
                    created.add(appointment);
                }
            }
        }
        if (seeded.slug().equals("studiobella")) {
            assignCounterCustomers(seeded, created, administrator, today);
        }
        appointmentRepository.saveAll(created);
        return created.size();
    }

    /**
     * Clientes de balcão (cadastrados pelo salão, sem conta) no Studio Bella, para demonstrar os
     * e-mails de agendamento: Carla tem e-mail e recebe os avisos com o telefone do salão (sem o
     * botão "Ver meus agendamentos"); Dora não tem e-mail e não recebe nada. Reaproveita
     * agendamentos já sorteados, cada um num dia diferente, para não criar conflito de horário.
     */
    private void assignCounterCustomers(SeededEstablishment seeded, List<Appointment> created,
                                        User administrator, LocalDate today) {
        Customer carla = customerRepository.save(Customer.create(
                seeded.establishment(), "Carla Nunes", "44991240001", "carla@balcao.demo"));
        Customer dora = customerRepository.save(Customer.create(
                seeded.establishment(), "Dora Pereira", "44991240002", null));
        // Os próximos agendamentos confirmados e os atendimentos concluídos mais recentes.
        assignTo(pickOnePerDay(created,
                appointment -> appointment.getStatus() == AppointmentStatus.CONFIRMED
                        && appointment.getStartsAt().toLocalDate().isAfter(today),
                Comparator.comparing(Appointment::getStartsAt), 3), carla, dora, administrator);
        assignTo(pickOnePerDay(created,
                appointment -> appointment.getStatus() == AppointmentStatus.COMPLETED,
                Comparator.comparing(Appointment::getStartsAt).reversed(), 3), carla, dora, administrator);
    }

    private static List<Appointment> pickOnePerDay(List<Appointment> created, Predicate<Appointment> filter,
                                                   Comparator<Appointment> order, int count) {
        List<Appointment> picked = new ArrayList<>();
        Set<LocalDate> days = new HashSet<>();
        created.stream().filter(filter).sorted(order).forEach(appointment -> {
            if (picked.size() < count && days.add(appointment.getStartsAt().toLocalDate())) {
                picked.add(appointment);
            }
        });
        return picked;
    }

    /** O último da lista fica com a Dora; os demais, com a Carla. Todos marcados pelo admin. */
    private static void assignTo(List<Appointment> appointments, Customer carla, Customer dora, User administrator) {
        for (int index = 0; index < appointments.size(); index++) {
            Appointment appointment = appointments.get(index);
            appointment.reassignCustomer(index == appointments.size() - 1 ? dora : carla);
            appointment.recordCreation(AppointmentSource.ADMIN, administrator);
        }
    }

    private void applyStatus(Appointment appointment, int dayOffset, User administrator) {
        double roll = random.nextDouble();
        LocalDateTime startsAt = appointment.getStartsAt();
        if (dayOffset > 0) {
            if (roll < 0.06) {
                appointment.cancelByAdministrator("Imprevisto na agenda do profissional.", administrator, LocalDateTime.now(clock));
            }
            return;
        }
        if (roll < 0.08) {
            appointment.cancelByAdministrator("Cliente pediu para remarcar.", administrator, startsAt.minusDays(1));
        } else if (roll < 0.12) {
            appointment.cancel(appointment.getCustomer().getUser(), appointment.getStartsAt());
        } else if (roll < 0.14) {
            appointment.complete(appointment.getEndsAt(), administrator);
            appointment.invalidate("Atendimento registrado por engano.", administrator, appointment.getEndsAt());
        } else if (roll < 0.19) {
            appointment.markNoShow(administrator, appointment.getStartsAt().plusMinutes(15));
        } else if (roll < 0.24 && dayOffset > -7) {
            // Alguns atendimentos recentes ficam pendentes de conclusão.
            return;
        } else {
            appointment.complete(appointment.getEndsAt(), administrator);
        }
    }

    private static LocalDate nextWorkingDay(LocalDate day) {
        LocalDate result = day;
        while (result.getDayOfWeek() == DayOfWeek.SUNDAY || result.getDayOfWeek() == DayOfWeek.SATURDAY
                || result.getDayOfWeek() == DayOfWeek.THURSDAY) {
            result = result.plusDays(1);
        }
        return result;
    }

    // ---------- documentos válidos ----------

    private String nextCpf() {
        String base = String.format("%09d", 390_000_000 + (++documentSequence) * 7919);
        int first = cpfDigit(base, 10);
        int second = cpfDigit(base + first, 11);
        return base + first + second;
    }

    private static int cpfDigit(String digits, int weight) {
        int sum = 0;
        for (char c : digits.toCharArray()) {
            sum += (c - '0') * weight--;
        }
        int rest = 11 - sum % 11;
        return rest >= 10 ? 0 : rest;
    }

    private String nextCnpj() {
        String base = String.format("%08d", 41_000_000 + (++documentSequence) * 104_729 % 9_000_000) + "0001";
        int first = cnpjDigit(base, new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        int second = cnpjDigit(base + first, new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        return base + first + second;
    }

    private static int cnpjDigit(String digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (digits.charAt(i) - '0') * weights[i];
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }

    private record SeededEstablishment(Establishment establishment, String slug, List<EstablishmentService> services,
                                       List<User> professionals, List<BlockedDay> blockedDays) {
    }

    private record BlockedDay(User professional, LocalDate day) {
    }
}
