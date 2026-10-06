package br.com.salonhub.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga as tarefas agendadas (lembretes de agendamento). Desligadas nos testes e no seed. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.notifications.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
