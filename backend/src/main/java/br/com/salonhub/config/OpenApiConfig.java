package br.com.salonhub.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI salonHubOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Meu Espaço API")
                        .version("v1")
                        .description("API da plataforma SaaS para gerenciamento de salões e barbearias."))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .name("bearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Informe o token JWT retornado pelo endpoint de login.")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
