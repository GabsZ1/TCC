package br.com.salonhub.config;

import jakarta.validation.Validator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Clock;

/** Registra os componentes usados na validação das requisições. */
@Configuration
public class ValidationConfig {

    @Bean
    Validator defaultValidator(Clock clock) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setConfigurationInitializer(configuration -> configuration.clockProvider(() -> clock));
        return validator;
    }
}
