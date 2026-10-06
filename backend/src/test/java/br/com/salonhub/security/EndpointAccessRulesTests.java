package br.com.salonhub.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que nenhum endpoint da aplicação fique sem regra de perfil: quem criar uma rota nova
 * sem {@code @PreAuthorize} (no método ou na classe do controller) faz este teste falhar.
 */
@SpringBootTest
class EndpointAccessRulesTests {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyApplicationEndpointMustDeclareAnAccessRule() {
        List<HandlerMethod> applicationEndpoints = handlerMapping.getHandlerMethods().values().stream()
                .filter(handler -> handler.getBeanType().getName().startsWith("br.com.salonhub."))
                .toList();

        List<String> withoutRule = applicationEndpoints.stream()
                .filter(handler -> !AnnotatedElementUtils.hasAnnotation(handler.getMethod(), PreAuthorize.class)
                        && !AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), PreAuthorize.class))
                .map(handler -> handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName())
                .sorted()
                .toList();

        assertThat(applicationEndpoints).isNotEmpty();
        assertThat(withoutRule).as("Endpoints sem @PreAuthorize").isEmpty();
    }
}
