package br.com.salonhub.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ponto de entrada do comando de popular o banco. Só existe com o profile "seed", então
 * nunca roda ao subir o backend normalmente nem nos testes.
 *
 * <pre>./mvnw spring-boot:run "-Dspring-boot.run.profiles=local,seed"</pre>
 *
 * Com o argumento --reset, apaga antes só os dados de demonstração e cria tudo de novo:
 * <pre>./mvnw spring-boot:run "-Dspring-boot.run.profiles=local,seed" "-Dspring-boot.run.arguments=--reset"</pre>
 */
@Slf4j
@Component
@Profile("seed")
@RequiredArgsConstructor
public class DemoSeedRunner implements ApplicationRunner {

    private final DemoDataSeeder seeder;
    private final DemoDataReset reset;
    private final PlatformTransactionManager transactionManager;
    private final ConfigurableApplicationContext context;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            // Contas de demonstração têm senha conhecida: nunca podem existir em produção.
            log.error("O seed não pode ser executado com o profile prod. Nenhum dado foi gravado.");
            int code = 1;
            System.exit(SpringApplication.exit(context, () -> code));
            return;
        }
        try {
            // Apagar e recriar ficam na mesma transação: se o seed falhar, os dados antigos voltam.
            DemoDataSeeder.Result result = new TransactionTemplate(transactionManager).execute(status -> {
                if (args.containsOption("reset")) {
                    DemoDataReset.Result removed = reset.reset();
                    log.info("Dados de demonstração anteriores removidos: {} estabelecimentos, {} usuários, {} agendamentos.",
                            removed.establishments(), removed.users(), removed.appointments());
                }
                return seeder.seed();
            });
            if (result == null) {
                log.info("Os dados de demonstração já existem. Nada foi alterado.");
                log.info("Para recriar só os dados de demonstração, rode de novo com \"-Dspring-boot.run.arguments=--reset\".");
            } else {
                log.info("Dados de demonstração criados: {} estabelecimentos, {} funcionários, {} clientes, {} agendamentos.",
                        result.establishments(), result.professionals(), result.clients(), result.appointments());
                log.info("Senha de todas as contas: {}", DemoDataSeeder.PASSWORD);
                log.info("Admin da plataforma: {} | Admin: {} | Profissional: {} | Cliente: {}",
                        DemoDataSeeder.SYSTEM_ADMIN_EMAIL, "admin@studiobella.demo",
                        "camila@studiobella.demo", "maria@cliente.demo");
            }
        } catch (RuntimeException exception) {
            log.error("Falha ao popular o banco", exception);
            exitCode = 1;
        }
        int code = exitCode;
        System.exit(SpringApplication.exit(context, () -> code));
    }
}
