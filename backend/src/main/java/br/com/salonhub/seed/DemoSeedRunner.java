package br.com.salonhub.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Ponto de entrada do comando de popular o banco. Só existe com o profile "seed", então
 * nunca roda ao subir o backend normalmente nem nos testes.
 *
 * <pre>./mvnw spring-boot:run "-Dspring-boot.run.profiles=local,seed"</pre>
 */
@Slf4j
@Component
@Profile("seed")
@RequiredArgsConstructor
public class DemoSeedRunner implements ApplicationRunner {

    private final DemoDataSeeder seeder;
    private final ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            DemoDataSeeder.Result result = seeder.seed();
            if (result == null) {
                log.info("Os dados de demonstração já existem. Nada foi alterado.");
                log.info("Para recriar do zero: docker compose down -v, docker compose up -d e rode o comando de novo.");
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
