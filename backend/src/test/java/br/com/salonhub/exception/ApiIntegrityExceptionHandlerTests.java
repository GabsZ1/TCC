package br.com.salonhub.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiIntegrityExceptionHandlerTests {
    @ParameterizedTest
    @CsvSource({
            "uk_users_email,23505,E-mail já cadastrado no sistema.",
            "uk_establishments_cnpj,23505,CNPJ já cadastrado.",
            "uk_salon_services_establishment_name,23505,Já existe um serviço com esse nome neste estabelecimento.",
            "uk_business_hours_establishment_day,23505,Já existe um horário cadastrado para este dia."
    })
    void shouldReturnConflictWithoutExposingDatabaseDetails(String constraint, String sqlState, String message) throws Exception {
        var exception = new DataIntegrityViolationException("internal SQL details",
                new ConstraintViolationException("internal SQL details", new SQLException("internal SQL details", sqlState), constraint));
        assertConflict(exception, message);
    }

    @ParameterizedTest
    @CsvSource("23505,Já existe um registro com os dados informados.")
    void shouldHandleUniqueConflictWithoutHibernate(String state, String message) throws Exception {
        assertConflict(new DataIntegrityViolationException("SQL", new SQLException("SQL", state)), message);
    }

    @Test
    void shouldNotTurnUnrelatedIntegrityFailuresIntoBusinessConflicts() {
        var exception = new DataIntegrityViolationException("unexpected not-null violation",
                new SQLException("unexpected not-null violation", "23502"));
        assertThatThrownBy(() -> new ApiExceptionHandler().handleDataIntegrity(exception)).isSameAs(exception);
    }

    private void assertConflict(DataIntegrityViolationException exception, String message) throws Exception {
        MockMvcBuilders.standaloneSetup(new FailingController(exception))
                .setControllerAdvice(new ApiExceptionHandler()).build()
                .perform(get("/integrity-test"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.fields").isEmpty());
    }

    @RestController
    static class FailingController {
        private final DataIntegrityViolationException exception;
        FailingController(DataIntegrityViolationException exception) { this.exception = exception; }
        @GetMapping("/integrity-test")
        public void fail() { throw exception; }
    }
}
