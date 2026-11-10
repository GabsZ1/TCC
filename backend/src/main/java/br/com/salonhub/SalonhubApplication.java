package br.com.salonhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Inicia a aplicação Spring Boot e a descoberta de seus componentes. */
@SpringBootApplication
@EntityScan(basePackages = "br.com.salonhub.entity")
@EnableJpaRepositories(basePackages = "br.com.salonhub.repository")
public class SalonhubApplication {

	public static void main(String[] args) {
		SpringApplication.run(SalonhubApplication.class, args);
	}

}
